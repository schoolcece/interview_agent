package com.ai.interview.service;

import com.ai.interview.dto.AuthRequest;
import com.ai.interview.dto.RegisterRequest;
import com.ai.interview.entity.User;
import com.ai.interview.repository.UserRepository;
import com.ai.interview.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证服务：负责用户登录、注册、以及 JWT 令牌的颁发。
 * <p>不持有 Spring Security 的 {@code AuthenticationManager}，
 * 采用手动校验密码 + 手动颁发 JWT 的轻量方案，避免引入复杂的 UserDetails 体系。
 */
@Service
@Slf4j
public class AuthService {

    private static final long TOKEN_EXPIRES_IN_MS = 86_400_000L; // 24h，与 application.yml 一致

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 用户登录：校验用户名 + 密码，更新最后登录时间，返回 JWT 令牌。
     *
     * @param request 包含 username / password 的登录请求
     * @return 含 accessToken、refreshToken、userId 等字段的 Map
     * @throws BadCredentialsException 用户名不存在、密码错误或账号未激活时抛出
     */
    public Map<String, Object> login(AuthRequest request) {
        validateLoginRequest(request);

        User user = userRepository.findByUsernameAndDeletedAtIsNull(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadCredentialsException("User account is not active");
        }

        user.touchLogin();
        userRepository.save(user);

        log.info("User {} logged in successfully", user.getUsername());
        return buildTokenResponse(user);
    }

    /**
     * 用户注册：唯一性校验 → 密码散列 → 持久化 → 颁发 JWT。
     *
     * @param request 注册信息（username / email / password / displayName 等）
     * @return 与 login() 格式一致的 Token 响应，注册即登录
     * @throws IllegalArgumentException 用户名或邮箱已被占用时抛出
     */
    public Map<String, Object> register(RegisterRequest request) {
        validateRegisterRequest(request);

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User newUser = User.register(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                request.getDisplayName(),
                request.getTargetDomain(),
                request.getExperienceLevel()
        );

        User saved = userRepository.save(newUser);
        log.info("New user {} registered successfully", saved.getUsername());
        return buildTokenResponse(saved);
    }

    /**
     * 按 ID 查询未被软删除的用户。
     *
     * @param userId 用户 ID，为 null 时直接返回 empty
     * @return 包含用户实体的 Optional，若不存在或已删除则为 empty
     */
    public Optional<User> getUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted());
    }

    /**
     * 根据用户信息生成 accessToken + refreshToken，并组装标准响应 Map。
     */
    private Map<String, Object> buildTokenResponse(User user) {
        String token = jwtTokenProvider.generateToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("accessToken", token);
        response.put("refreshToken", refreshToken);
        response.put("userId", user.getId());
        response.put("email", user.getEmail());
        response.put("username", user.getUsername());
        response.put("expiresIn", TOKEN_EXPIRES_IN_MS);
        return response;
    }

    /** 基础参数非空校验，避免 NPE 传递到 Repository 层。 */
    private void validateLoginRequest(AuthRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Username and password are required");
        }
    }

    /** 注册字段非空校验；密码强度校验放在前端，后端仅保证不为空。 */
    private void validateRegisterRequest(RegisterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Register request is required");
        }
        if (!StringUtils.hasText(request.getUsername())) {
            throw new IllegalArgumentException("Username is required");
        }
        if (!StringUtils.hasText(request.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }
        if (!StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Password is required");
        }
    }
}
