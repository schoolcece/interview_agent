package com.ai.interview.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private static final String TEST_SECRET =
            "01234567890123456789012345678901234567890123456789012345678901234567890123456789";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 600_000L);
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshExpiration", 1_200_000L);
    }

    @Test
    void generateToken_shouldBeParsableAndValid() {
        String token = jwtTokenProvider.generateToken(42L, "u@example.com");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromJWT(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getEmailFromJWT(token)).isEqualTo("u@example.com");
    }

    @Test
    void generateRefreshToken_shouldBeValid() {
        String token = jwtTokenProvider.generateRefreshToken(42L);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUserIdFromJWT(token)).isEqualTo(42L);
    }

    @Test
    void validateToken_shouldReturnFalse_whenTokenInvalid() {
        assertThat(jwtTokenProvider.validateToken("bad-token")).isFalse();
    }
}
