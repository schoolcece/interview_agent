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

    public Optional<User> getUserById(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted());
    }

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

    private void validateLoginRequest(AuthRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            throw new IllegalArgumentException("Username and password are required");
        }
    }

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
