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

@RestController
@RequestMapping("/interview")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    @GetMapping("/sessions")
    public ResponseEntity<Page<InterviewSession>> listSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(interviewService.listMySessions(page, size));
    }

    @PostMapping("/session/create")
    public ResponseEntity<InterviewSession> createSession(@RequestBody CreateInterviewSessionRequest request) {
        return ResponseEntity.ok(interviewService.createInterviewSession(request));
    }

    @PostMapping("/{sessionId}/start")
    public ResponseEntity<Map<String, Object>> startInterview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.startInterview(sessionId));
    }

    @PostMapping("/{sessionId}/turn")
    public ResponseEntity<Map<String, Object>> submitTurn(
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> request) {
        String userResponse = request.get("response");
        String audioPath = request.get("audioPath");
        return ResponseEntity.ok(interviewService.submitTurn(sessionId, userResponse, audioPath));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<InterviewSession> getSession(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSession(sessionId));
    }

    @GetMapping("/{sessionId}/turns")
    public ResponseEntity<List<InterviewTurn>> getSessionTurns(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.getSessionTurns(sessionId));
    }

    @PostMapping("/{sessionId}/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateInterview(@PathVariable Long sessionId) {
        return ResponseEntity.ok(interviewService.triggerEvaluation(sessionId));
    }
}
