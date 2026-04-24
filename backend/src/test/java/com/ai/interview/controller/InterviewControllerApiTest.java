package com.ai.interview.controller;

import com.ai.interview.dto.CreateInterviewSessionRequest;
import com.ai.interview.entity.InterviewSession;
import com.ai.interview.entity.SessionStatus;
import com.ai.interview.entity.embeddable.SessionConfig;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.service.InterviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "jwt.secret=01234567890123456789012345678901234567890123456789012345678901234567890123456789",
        "jwt.expiration=86400000",
        "jwt.refresh-expiration=2592000000",
        "cors.allowed-origins=http://localhost:5173"
})
class InterviewControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InterviewService interviewService;

    @Test
    void createSession_shouldReturn200() throws Exception {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setStatus(SessionStatus.PLANNING);
        session.setCurrentTurn(0);
        session.setTotalTurns(10);

        when(interviewService.createInterviewSession(any(CreateInterviewSessionRequest.class))).thenReturn(session);

        SessionConfig config = new SessionConfig();
        config.setDomain("backend");

        CreateInterviewSessionRequest request = new CreateInterviewSessionRequest();
        request.setConfig(config);

        mockMvc.perform(post("/interview/session/create")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLANNING"))
                .andExpect(jsonPath("$.currentTurn").value(0));
    }

    @Test
    void startInterview_shouldReturn200() throws Exception {
        when(interviewService.startInterview(anyLong())).thenReturn(Map.of("question", "introduce yourself"));

        mockMvc.perform(post("/interview/{sessionId}/start", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("introduce yourself"));
    }

    @Test
    void submitTurn_shouldReturn200() throws Exception {
        when(interviewService.submitTurn(anyLong(), any(), any())).thenReturn(Map.of("score", 85));

        mockMvc.perform(post("/interview/{sessionId}/turn", 1L)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "response", "answer",
                                "audioPath", "audio.wav"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85));
    }

    @Test
    void getSession_shouldReturn404_whenSessionMissing() throws Exception {
        when(interviewService.getSession(anyLong()))
                .thenThrow(new ResourceNotFoundException("Interview session not found"));

        mockMvc.perform(get("/interview/{sessionId}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Interview session not found"));
    }

    @Test
    void evaluate_shouldReturn200() throws Exception {
        when(interviewService.triggerEvaluation(anyLong())).thenReturn(Map.of("overall_score", 88));

        mockMvc.perform(post("/interview/{sessionId}/evaluate", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall_score").value(88));
    }
}
