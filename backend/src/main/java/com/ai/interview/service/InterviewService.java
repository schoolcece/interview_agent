package com.ai.interview.service;

import com.ai.interview.client.AgentServiceClient;
import com.ai.interview.dto.CreateInterviewSessionRequest;
import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.InterviewTurn;
import com.ai.interview.entity.SessionStatus;
import com.ai.interview.entity.embeddable.SessionConfig;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.InterviewTurnRepository;
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
    private final AgentServiceClient agentServiceClient;
    private final LoginUserContextService loginUserContextService;

    public InterviewService(InterviewSessionRepository sessionRepository,
                            InterviewTurnRepository turnRepository,
                            AgentServiceClient agentServiceClient,
                            LoginUserContextService loginUserContextService) {
        this.sessionRepository = sessionRepository;
        this.turnRepository = turnRepository;
        this.agentServiceClient = agentServiceClient;
        this.loginUserContextService = loginUserContextService;
    }

    public InterviewSession createInterviewSession(CreateInterviewSessionRequest request) {
        Long userId = loginUserContextService.requireUserId();
        if (request == null || request.getConfig() == null) {
            throw new IllegalArgumentException("config is required");
        }
        SessionConfig config = request.getConfig();
        if (config.getDomain() == null || config.getDomain().isBlank()) {
            throw new IllegalArgumentException("config.domain is required");
        }

        InterviewSession session = InterviewSession.create(
                userId, request.getResumeId(), request.getTitle(), config);

        InterviewSession saved = sessionRepository.save(session);
        log.info("Interview session created: id={}, user={}", saved.getId(), userId);
        return saved;
    }

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

    public InterviewSession getSession(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        return requireOwnedSession(sessionId, userId);
    }

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

    public Page<InterviewSession> listMySessions(int page, int size) {
        Long userId = loginUserContextService.requireUserId();
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }

    public List<InterviewTurn> getSessionTurns(Long sessionId) {
        Long userId = loginUserContextService.requireUserId();
        requireOwnedSession(sessionId, userId);
        return turnRepository.findBySessionIdOrderByTurnNoAsc(sessionId);
    }

    private InterviewSession requireOwnedSession(Long sessionId, Long userId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId is required");
        }
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Interview session not found"));
    }
}
