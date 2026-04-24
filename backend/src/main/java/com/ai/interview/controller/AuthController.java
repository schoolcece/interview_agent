package com.ai.interview.controller;

import com.ai.interview.dto.AuthRequest;
import com.ai.interview.dto.RegisterRequest;
import com.ai.interview.entity.User;
import com.ai.interview.exception.ResourceNotFoundException;
import com.ai.interview.security.LoginUserContextService;
import com.ai.interview.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 注意：Controller 层不再包裹 try/catch。
 * 异常统一由 {@link com.ai.interview.exception.GlobalExceptionHandler} 处理，
 * 避免 500 错误被误吞为 400/401。
 */
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final LoginUserContextService loginUserContextService;

    public AuthController(AuthService authService, LoginUserContextService loginUserContextService) {
        this.authService = authService;
        this.loginUserContextService = loginUserContextService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile() {
        Long userId = loginUserContextService.requireUserId();
        User user = authService.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(toProfileResponse(user));
    }

    private Map<String, Object> toProfileResponse(User user) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("email", user.getEmail());
        profile.put("displayName", user.getDisplayName());
        profile.put("avatarUrl", user.getAvatarUrl());
        profile.put("targetDomain", user.getTargetDomain());
        profile.put("experienceLevel", user.getExperienceLevel());
        profile.put("role", user.getRole());
        profile.put("active", user.getActive());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("lastLoginAt", user.getLastLoginAt());
        return profile;
    }
}
