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
 * 认证相关接口：登录 / 注册 / 查看个人资料。
 *
 * <pre>
 * POST /auth/login     登录，返回 JWT 令牌（无需认证）
 * POST /auth/register  注册，成功后自动返回令牌（无需认证）
 * GET  /auth/profile   查看当前登录用户的个人信息（需要 JWT）
 * </pre>
 *
 * <p>注意：Controller 层不再包裹 try/catch。
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

    /** 用户名密码登录，返回 accessToken / refreshToken 等信息。 */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** 新用户注册，注册成功后直接返回 Token（免二次登录）。 */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** 获取当前登录用户的基本资料（需在 Header 中携带 Bearer Token）。 */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile() {
        Long userId = loginUserContextService.requireUserId();
        User user = authService.getUserById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return ResponseEntity.ok(toProfileResponse(user));
    }

    /** 将 User 实体转换为脱敏的 API 响应 Map（不暴露 passwordHash 等敏感字段）。 */
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
