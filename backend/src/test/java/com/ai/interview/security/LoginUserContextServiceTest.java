package com.ai.interview.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginUserContextServiceTest {

    private LoginUserContextService loginUserContextService;

    @BeforeEach
    void setUp() {
        loginUserContextService = new LoginUserContextService();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void requireUserId_shouldThrow_whenContextMissing() {
        assertThatThrownBy(() -> loginUserContextService.requireUserId())
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    void requireUserId_shouldReturnLong_whenContextValid() {
        UserContext.setUserId(42L);

        Long result = loginUserContextService.requireUserId();

        assertThat(result).isEqualTo(42L);
    }
}
