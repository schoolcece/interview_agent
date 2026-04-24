package com.ai.interview.controller;

import com.ai.interview.dto.AuthRequest;
import com.ai.interview.dto.RegisterRequest;
import com.ai.interview.entity.User;
import com.ai.interview.entity.UserRole;
import com.ai.interview.security.LoginUserContextService;
import com.ai.interview.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
        "server.servlet.context-path=",
        "jwt.secret=01234567890123456789012345678901234567890123456789012345678901234567890123456789",
        "jwt.expiration=86400000",
        "jwt.refresh-expiration=2592000000",
        "cors.allowed-origins=http://localhost:5173"
})
class AuthControllerApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private LoginUserContextService loginUserContextService;

    @Test
    void login_shouldReturn200() throws Exception {
        when(authService.login(any(AuthRequest.class)))
                .thenReturn(Map.of("accessToken", "token", "refreshToken", "refresh", "username", "alice"));

        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setPassword("pwd");

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token"));
    }

    @Test
    void register_shouldReturn400_whenServiceThrows() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new IllegalArgumentException("Username already exists"));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice@example.com");
        request.setPassword("pwd");

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username already exists"));
    }

    @Test
    void profile_shouldReturn200_whenUserExists() throws Exception {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setActive(true);
        user.setRole(UserRole.USER);

        when(loginUserContextService.requireUserId()).thenReturn(userId);
        when(authService.getUserById(userId)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void profile_shouldReturn403_whenUnauthenticated() throws Exception {
        when(loginUserContextService.requireUserId()).thenThrow(new AccessDeniedException("Unauthorized"));

        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
