package com.ai.interview.websocket;

import com.ai.interview.security.JwtHandshakeInterceptor;
import com.ai.interview.security.UserContext;
import com.ai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java 侧 WebSocket 消息处理器。
 *
 * <h3>鉴权方式</h3>
 * <p>userId 由 {@link JwtHandshakeInterceptor} 在握手阶段验证并写入
 * {@link WebSocketSession#getAttributes()}，本 Handler 直接读取，
 * 无需再访问 ThreadLocal（ThreadLocal 在 WebSocket 线程下不可靠）。
 *
 * <h3>⚠️ Phase 0 过渡说明</h3>
 * <p>本 Handler 通过 HTTP 调用 Python Agent；Phase 1 后真实的语音交互将由
 * 前端直连 Python WebSocket，本 Handler 届时退役（保留仅供文本 MVP 过渡）。
 */
@Component
@Slf4j
public class InterviewWebSocketHandler extends TextWebSocketHandler {

    private final InterviewService interviewService;
    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    public InterviewWebSocketHandler(InterviewService interviewService, ObjectMapper objectMapper) {
        this.interviewService = interviewService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        log.info("WS connection established: sessionId={}, userId={}", session.getId(), userId);
        sessionMap.put(session.getId(), session);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = getUserId(session);

        try {
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
            String messageType = (String) payload.get("type");
            if (messageType == null) {
                sendErrorMessage(session, "Missing 'type' field");
                return;
            }

            // 将 userId 注入 UserContext，让 Service 层的 requireUserId() 可以通过
            UserContext.setUserId(userId);
            try {
                switch (messageType) {
                    case "start_interview" -> handleStartInterview(session, payload);
                    case "submit_turn"     -> handleSubmitTurn(session, payload);
                    case "end_interview"   -> handleEndInterview(session, payload);
                    case "ping"            -> session.sendMessage(new TextMessage("{\"type\":\"pong\"}"));
                    default -> log.warn("Unknown WS message type: {}", messageType);
                }
            } finally {
                UserContext.clear();
            }
        } catch (Exception e) {
            log.error("Error handling WS message: {}", e.getMessage(), e);
            sendErrorMessage(session, "Error processing message: " + e.getMessage());
        }
    }

    private void handleStartInterview(WebSocketSession session, Map<String, Object> payload) throws IOException {
        Long sessionId = asLong(payload.get("sessionId"));
        try {
            Map<String, Object> result = interviewService.startInterview(sessionId);
            sendSuccessMessage(session, "interview_started", result);
        } catch (Exception e) {
            log.error("Error starting interview: {}", e.getMessage());
            sendErrorMessage(session, "Failed to start interview: " + e.getMessage());
        }
    }

    private void handleSubmitTurn(WebSocketSession session, Map<String, Object> payload) throws IOException {
        Long sessionId = asLong(payload.get("sessionId"));
        String userResponse = (String) payload.get("response");
        String audioPath = (String) payload.get("audioPath");
        try {
            Map<String, Object> result = interviewService.submitTurn(sessionId, userResponse, audioPath);
            sendSuccessMessage(session, "turn_processed", result);
        } catch (Exception e) {
            log.error("Error submitting turn: {}", e.getMessage());
            sendErrorMessage(session, "Failed to process turn: " + e.getMessage());
        }
    }

    private void handleEndInterview(WebSocketSession session, Map<String, Object> payload) throws IOException {
        Long sessionId = asLong(payload.get("sessionId"));
        try {
            Map<String, Object> result = interviewService.triggerEvaluation(sessionId);
            sendSuccessMessage(session, "interview_evaluated", result);
        } catch (Exception e) {
            log.error("Error ending interview: {}", e.getMessage());
            sendErrorMessage(session, "Failed to evaluate interview: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("WS connection closed: sessionId={}, status={}", session.getId(), status);
        sessionMap.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WS transport error: sessionId={}, error={}", session.getId(), exception.getMessage(), exception);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Long getUserId(WebSocketSession session) {
        Object v = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        return (v instanceof Long l) ? l : null;
    }

    private Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(v)); }
        catch (NumberFormatException ex) { return null; }
    }

    private void sendSuccessMessage(WebSocketSession session, String type, Map<String, Object> data) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("type", type);
        resp.put("status", "success");
        resp.put("data", data);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
    }

    private void sendErrorMessage(WebSocketSession session, String msg) throws IOException {
        Map<String, Object> resp = new HashMap<>();
        resp.put("type", "error");
        resp.put("status", "error");
        resp.put("message", msg);
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
    }
}
