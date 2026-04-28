package com.ai.interview.controller;

import com.ai.interview.dto.CreateInterviewSessionRequest;
import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.InterviewTurn;
import com.ai.interview.service.InterviewService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 面试核心流程接口。
 *
 * <pre>
 * GET  /interview/sessions              分页查询当前用户的 session 列表
 * POST /interview/session/create        创建新 session（进入 PLANNING 状态）
 * POST /interview/{sessionId}/start     启动面试（调用 AI 生成计划，进入 IN_PROGRESS）
 * POST /interview/{sessionId}/turn      提交本轮回答，获取评分与下一题
 * GET  /interview/{sessionId}           查询 session 详情（含状态、进度等）
 * GET  /interview/{sessionId}/turns     查询该 session 下所有问答轮次
 * POST /interview/{sessionId}/evaluate  触发整体评估，生成面试报告
 * </pre>
 */
@RestController
@RequestMapping("/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /** 分页查询当前用户的所有面试 session，默认第 0 页、每页 10 条。 */
    @GetMapping("/sessions")
    public ResponseEntity<Page<InterviewSession>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(interviewService.listMySessions(page, size));
    }

    /** 创建新的面试 session，请求体须包含 config.domain 字段。 */
    @PostMapping("/session/create")
    public ResponseEntity<InterviewSession> createSession(@RequestBody CreateInterviewSessionRequest request) {
        return ResponseEntity.ok(interviewService.createInterviewSession(request));
    }

    /**
     * 启动面试：触发 AI Planning，session 状态从 PLANNING 切换为 IN_PROGRESS。
     * 返回 AI 生成的面试计划（含题目列表、知识点覆盖范围等）。
     */
    @PostMapping("/{sessionId}/start")
    public ResponseEntity<Map<String, Object>> startInterview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.startInterview(sessionId));
    }

    /**
     * 提交本轮用户回答。
     * 请求体 JSON 字段：{@code response}（文字回答）和可选的 {@code audioPath}（音频路径）。
     * 返回本轮评分详情与下一个问题（若面试已结束则返回 is_complete=true）。
     */
    @PostMapping("/{sessionId}/turn")
    public ResponseEntity<Map<String, Object>> submitTurn(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> request) {
        String userResponse = request.get("response");
        String audioPath = request.get("audioPath");
        return ResponseEntity.ok(interviewService.submitTurn(sessionId, userResponse, audioPath));
    }

    /** 查询指定 session 的状态、进度、配置等元数据。 */
    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSession> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSession(sessionId));
    }

    /** 获取该 session 下所有问答轮次（含题目、用户回答、评分等），按轮次号升序。 */
    @GetMapping("/{sessionId}/turns")
    public ResponseEntity<List<InterviewTurn>> getSessionTurns(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSessionTurns(sessionId));
    }

    /** 触发 AI 整体评估，生成面试总结报告（建议在 session 状态为 COMPLETED 后调用）。 */
    @PostMapping("/{sessionId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateInterview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.triggerEvaluation(sessionId));
    }
}
