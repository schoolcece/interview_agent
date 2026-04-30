package com.ai.interview.service;

import com.ai.interview.client.AgentServiceClient;
import com.ai.interview.dto.CreateInterviewSessionRequest;
import com.ai.interview.entity.AnalysisStatus;
import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.InterviewTurn;
import com.ai.interview.entity.Resume;
import com.ai.interview.entity.SessionStatus;
import com.ai.interview.entity.embeddable.SessionConfig;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.InterviewTurnRepository;
import com.ai.interview.repository.ResumeRepository;
import com.ai.interview.security.LoginUserContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试会话业务逻辑。
 * <p>当前实现是 Phase 0 的过渡版本：仍通过 HTTP 调用 Python Agent 的 Planning/Interview/Evaluation 端点。
 * Phase 1 将按 ARCHITECTURE §2.2 演进为前端直连 Python，Java 仅管 session 元数据与事件消费。
 */
@Service
@Slf4j
public class InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewTurnRepository turnRepository;
    private final ResumeRepository resumeRepository;
    private final AgentServiceClient agentServiceClient;
    private final LoginUserContextService loginUserContextService;

    public InterviewService(InterviewSessionRepository sessionRepository,
                            InterviewTurnRepository turnRepository,
                            ResumeRepository resumeRepository,
                            AgentServiceClient agentServiceClient,
                            LoginUserContextService loginUserContextService) {
        this.sessionRepository = sessionRepository;
        this.turnRepository = turnRepository;
        this.resumeRepository = resumeRepository;
        this.agentServiceClient = agentServiceClient;
        this.loginUserContextService = loginUserContextService;
    }

    /**
     * 创建面试会话，初始状态为 PLANNING（等待 AI 生成面试计划）。
     *
     * @param request 包含 config（领域/总轮次等）和可选的 resumeId
     * @return 已持久化的 InterviewSession 实体
     * @throws IllegalArgumentException config 或 config.domain 为空时抛出
     */
    public InterviewSession createInterviewSession(CreateInterviewSessionRequest request) {
        Long userId = loginUserContextService.requireUserId();
        if (request == null || request.getConfig() == null) {
            throw new IllegalArgumentException("config is required");
        }
        SessionConfig config = request.getConfig();
        if (config.getDomain() == null || config.getDomain().isBlank()) {
            throw new IllegalArgumentException("config.domain is required");
        }

        // 若指定了 resumeId，校验简历已解析完成，否则拒绝创建（避免 Planner 拿到空简历）
        if (request.getResumeId() != null) {
            Resume resume = resumeRepository.findByIdAndUserIdAndDeletedAtIsNull(
                    request.getResumeId(), userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
            if (resume.getAnalysisStatus() == AnalysisStatus.PENDING
                    || resume.getAnalysisStatus() == AnalysisStatus.PARSING) {
                throw new IllegalArgumentException(
                        "Resume is still being analyzed. Please wait until analysis is complete.");
            }
            if (resume.getAnalysisStatus() == AnalysisStatus.FAILED) {
                throw new IllegalArgumentException(
                        "Resume analysis failed. Please re-upload the file.");
            }
        }

        InterviewSession session = InterviewSession.create(
                userId, request.getResumeId(), request.getTitle(), config);

        InterviewSession saved = sessionRepository.save(session);
        log.info("Interview session created: id={}, user={}", saved.getId(), userId);
        return saved;
    }

    /**
     * 启动面试：调用 Python Planning 服务生成面试计划，并将 session 状态切换为 IN_PROGRESS。
     * <p>若 Python 服务调用失败，session 状态置为 FAILED 并抛出异常。
     *
     * @param sessionId 待启动的 session ID
     * @return Python Planning 服务返回的计划内容 Map（含问题列表、知识点覆盖等）
     */
    public Map<String, Object> startInterview(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        InterviewSession session = requireOwnedSession(sessionId, userId);

        Map<String, Object> planRequest = new HashMap<>();
        planRequest.put("user_id", userId);
        planRequest.put("session_id", session.getId());
        planRequest.put("resume_id", session.getResumeId());
        planRequest.put("config", session.getConfig());

        try {
            Map<String, Object> planResponse = agentServiceClient.callPlanningService(planRequest);

            // 与 Python 写入 DB 的 plan/total_turns 对齐，避免 Hibernate 快照仍为「创建会话时的旧值」
            applyPlanningResult(session, planResponse);

            session.setStatus(SessionStatus.IN_PROGRESS);
            session.setCurrentTurn(1);
            session.setStartedAt(Instant.now());
            sessionRepository.save(session);
            return planResponse;
        } catch (Exception e) {
            log.error("Error calling planning service for session {}", sessionId, e);
            session.setStatus(SessionStatus.FAILED);
            sessionRepository.save(session);
            throw new RuntimeException("Failed to start interview", e);
        }
    }

    /**
     * 提交用户的当前轮回答，转发给 Python Interview 服务进行评分并获取下一个问题。
     * <p>若 Python 返回 {@code is_complete=true}，自动将 session 状态切换为 COMPLETED 并记录结束时间。
     *
     * @param sessionId    所属 session ID
     * @param userResponse 用户的文字回答（语音场景由前端 ASR 转文字后传入）
     * @param audioPath    用户音频文件在 MinIO 中的路径（纯文字场景可为 null）
     * @return Python Interview 服务返回的 Map（含下一题、评分结果等）
     */
    public Map<String, Object> submitTurn(Long sessionId, String userResponse, String audioPath) {
        Long userId = loginUserContextService.requireUserId();
        InterviewSession session = requireOwnedSession(sessionId, userId);

        int currentTurn = session.getCurrentTurn() == null ? 0 : session.getCurrentTurn();

        Map<String, Object> turnRequest = new HashMap<>();
        turnRequest.put("session_id", sessionId);
        turnRequest.put("user_id", userId);
        turnRequest.put("user_response", userResponse);
        turnRequest.put("audio_path", audioPath);
        turnRequest.put("current_turn", currentTurn);

        try {
            Map<String, Object> turnResponse = agentServiceClient.callInterviewService(turnRequest);

            session.setCurrentTurn(currentTurn + 1);

            Object completeFlag = turnResponse.getOrDefault("is_complete", false);
            boolean isComplete = Boolean.TRUE.equals(completeFlag)
                    || "true".equalsIgnoreCase(String.valueOf(completeFlag));
            if (isComplete) {
                session.setStatus(SessionStatus.COMPLETED);
                session.setEndedAt(Instant.now());
                if (session.getStartedAt() != null) {
                    session.setDurationSeconds((int) (session.getEndedAt().getEpochSecond()
                            - session.getStartedAt().getEpochSecond()));
                }
            }

            sessionRepository.save(session);
            return turnResponse;
        } catch (Exception e) {
            log.error("Error calling interview service for session {}", sessionId, e);
            throw new RuntimeException("Failed to process interview turn", e);
        }
    }

    /**
     * 获取当前用户的指定 session（鉴权：只能查看自己的）。
     *
     * @throws ResourceNotFoundException session 不存在或不属于当前用户时抛出
     */
    public InterviewSession getSession(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        return requireOwnedSession(sessionId, userId);
    }

    /**
     * 触发面试整体评估：调用 Python Evaluation 服务生成综合评估报告。
     * <p>通常在所有轮次完成后由前端主动调用，或在 submitTurn 检测到 is_complete 时自动触发。
     *
     * @param sessionId 待评估的 session ID
     * @return Python 返回的评估报告 Map（含总分、薄弱知识点等）
     */
    public Map<String, Object> triggerEvaluation(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        requireOwnedSession(sessionId, userId);

        Map<String, Object> evalRequest = new HashMap<>();
        evalRequest.put("session_id", sessionId);
        evalRequest.put("user_id", userId);

        try {
            return agentServiceClient.callEvaluationService(evalRequest);
        } catch (Exception e) {
            log.error("Error calling evaluation service for session {}", sessionId, e);
            throw new RuntimeException("Failed to evaluate interview", e);
        }
    }

    /**
     * 分页查询当前用户的所有面试 session，按创建时间倒序排列。
     *
     * @param page 页码（从 0 开始）
     * @param size 每页条数
     * @return 分页结果（包含 content、totalElements 等 Page 元信息）
     */
    public Page<InterviewSession> listMySessions(int page, int size) {
        Long userId = loginUserContextService.requireUserId();
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    /**
     * 获取指定 session 下所有问答轮次，按 turnNo 升序排列（第 1 轮在前）。
     * <p>先校验 session 归属权，再查询 turns，防止越权访问。
     */
    public List<InterviewTurn> getSessionTurns(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        requireOwnedSession(sessionId, userId);
        return turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);
    }

    /**
     * 将 Agent Planning 响应合并进当前会话实体，保证内存状态与 Python 已提交的事务一致，
     * 并与 {@link com.ai.interview.entity.InterviewSession} 上的 {@code @DynamicUpdate} 配合，
     * 避免后续 UPDATE 用陈旧快照覆盖 {@code plan/total_turns}。
     */
    @SuppressWarnings("unchecked")
    private void applyPlanningResult(InterviewSession session, Map<String, Object> planResponse) {
        if (planResponse == null) {
            return;
        }
        Object planObj = planResponse.get("plan");
        if (planObj instanceof Map<?, ?> raw) {
            session.setPlan((Map<String, Object>) raw);
            Object tt = raw.get("total_turns");
            if (tt instanceof Number n) {
                session.setTotalTurns(n.intValue());
            }
        }
    }

    /**
     * 查询 session 并验证其归属权，任何 Service 方法操作 session 前必须调用此方法。
     *
     * @throws IllegalArgumentException  sessionId 为 null 时抛出
     * @throws ResourceNotFoundException session 不存在或不属于 userId 时抛出（统一 404，不泄露资源存在性）
     */
    private InterviewSession requireOwnedSession(Long sessionId, Long userId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found"));
    }
}
