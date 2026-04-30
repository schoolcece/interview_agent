package com.ai.interview.service;

import com.ai.interview.client.AgentServiceClient;
import com.ai.interview.dto.CreateInterviewSessionRequest;
import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.SessionStatus;
import com.ai.interview.entity.embeddable.SessionConfig;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.repository.InterviewSessionRepository;
import com.ai.interview.repository.InterviewTurnRepository;
import com.ai.interview.repository.ResumeRepository;
import com.ai.interview.security.LoginUserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InterviewServiceTest {

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewTurnRepository turnRepository;
    @Mock
    private AgentServiceClient agentServiceClient;
    @Mock
    private LoginUserContextService loginUserContextService;
    @Mock
    private ResumeRepository resumeRepository;

    private InterviewService interviewService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUp() {
        interviewService = new InterviewService(sessionRepository, turnRepository, resumeRepository, agentServiceClient, loginUserContextService);
        when(loginUserContextService.requireUserId()).thenReturn(USER_ID);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // createInterviewSession
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createInterviewSession_shouldCreatePlanningSession() {
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionConfig config = new SessionConfig();
        config.setDomain("backend");
        config.setTotalTurns(10);

        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setResumeId(null);
        request.setTitle("Backend Round 1");
        request.setConfig(config);

        InterviewSession session = interviewService.createInterviewSession(request);

        assertThat(session.getUserId()).isEqualTo(USER_ID);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.PLANNING);
        assertThat(session.getCurrentTurn()).isEqualTo(0);
        assertThat(session.getTotalTurns()).isEqualTo(10);
    }

    @Test
    void createInterviewSession_shouldThrow_whenConfigMissing() {
        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setConfig(null);

        assertThatThrownBy(() -> interviewService.createInterviewSession(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // startInterview
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void startInterview_shouldThrow_whenSessionNotFound() {
        Long sessionId = 99L;
        when(sessionRepository.findByIdAndUserId(sessionId, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.startInterview(sessionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Interview session not found");
    }

    @Test
    void startInterview_shouldMoveToInProgress() {
        Long sessionId = 10L;

        SessionConfig config = new SessionConfig();
        config.setDomain("backend");
        InterviewSession session = InterviewSession.create(USER_ID, null, "test", config);
        session.setId(sessionId);

        when(sessionRepository.findByIdAndUserId(sessionId, USER_ID)).thenReturn(Optional.of(session));
        when(agentServiceClient.callPlanningService(anyMap())).thenReturn(Map.of("plan", "ok"));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> response = interviewService.startInterview(sessionId);

        assertThat(response.get("plan")).isEqualTo("ok");
        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(captor.getValue().getCurrentTurn()).isEqualTo(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // submitTurn
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void submitTurn_shouldCompleteSession_whenAgentReturnsComplete() {
        Long sessionId = 20L;

        InterviewSession session = new InterviewSession();
        session.setId(sessionId);
        session.setUserId(USER_ID);
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setCurrentTurn(2);
        session.setTotalTurns(15);

        when(sessionRepository.findByIdAndUserId(sessionId, USER_ID)).thenReturn(Optional.of(session));
        when(agentServiceClient.callInterviewService(anyMap())).thenReturn(Map.of("is_complete", true));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

        interviewService.submitTurn(sessionId, "my answer", null);

        ArgumentCaptor<InterviewSession> captor = ArgumentCaptor.forClass(InterviewSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getCurrentTurn()).isEqualTo(3);
        assertThat(captor.getValue().getStatus()).isEqualTo(SessionStatus.COMPLETED);
        assertThat(captor.getValue().getEndedAt()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getSession
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getSession_shouldThrow_whenSessionNotFound() {
        Long sessionId = 999L;
        when(sessionRepository.findByIdAndUserId(sessionId, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interviewService.getSession(sessionId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Interview session not found");
    }
}
