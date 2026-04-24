package com.ai.interview.service;

import com.ai.interview.dto.AuthRequest;
import com.ai.interview.dto.RegisterRequest;
import com.ai.interview.entity.User;
import com.ai.interview.entity.UserRole;
import com.ai.interview.repository.UserRepository;
import com.ai.interview.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtTokenProvider);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // login
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void login_shouldReturnTokens_whenCredentialsValid() {
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("encoded");
        user.setActive(true);
        user.setRole(UserRole.USER);

        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setPassword("pwd");

        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("pwd", "encoded")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtTokenProvider.generateToken(eq(userId), eq("alice@example.com"))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(userId))).thenReturn("refresh-token");

        Map<String, Object> response = authService.login(request);

        assertThat(response.get("accessToken")).isEqualTo("access-token");
        assertThat(response.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(response.get("username")).isEqualTo("alice");
    }

    @Test
    void login_shouldThrow_whenPasswordInvalid() {
        User user = new User();
        user.setUsername("alice");
        user.setPasswordHash("encoded");
        user.setActive(true);
        user.setRole(UserRole.USER);

        AuthRequest request = new AuthRequest();
        request.setUsername("alice");
        request.setPassword("wrong");

        when(userRepository.findByUsernameAndDeletedAtIsNull("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    @Test
    void login_shouldThrow_whenUserNotFound() {
        AuthRequest request = new AuthRequest();
        request.setUsername("ghost");
        request.setPassword("pwd");

        when(userRepository.findByUsernameAndDeletedAtIsNull("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid username or password");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // register
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void register_shouldCreateUser_whenInputValid() {
        Long userId = 2L;

        RegisterRequest request = new RegisterRequest();
        request.setUsername("bob");
        request.setEmail("bob@example.com");
        request.setPassword("pwd");
        request.setDisplayName("Bob Lee");
        request.setTargetDomain("Backend");
        request.setExperienceLevel("junior");

        when(userRepository.existsByUsername("bob")).thenReturn(false);
        when(userRepository.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("pwd")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(userId);
            return u;
        });
        when(jwtTokenProvider.generateToken(eq(userId), eq("bob@example.com"))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(eq(userId))).thenReturn("refresh-token");

        Map<String, Object> response = authService.register(request);

        assertThat(response.get("userId")).isEqualTo(userId);
        assertThat(response.get("username")).isEqualTo("bob");
        assertThat(response.get("accessToken")).isEqualTo("access-token");
    }

    @Test
    void register_shouldThrow_whenUsernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setEmail("alice2@example.com");
        request.setPassword("pwd");

        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getUserById
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getUserById_shouldReturnEmpty_whenIdIsNull() {
        Optional<User> result = authService.getUserById(null);
        assertThat(result).isEmpty();
    }

    @Test
    void getUserById_shouldReturnUser_whenExists() {
        Long userId = 3L;
        User user = new User();
        user.setId(userId);
        user.setUsername("charlie");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        Optional<User> result = authService.getUserById(userId);
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("charlie");
    }
}
