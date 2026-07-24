package com.duoinfra.backend.user.infra;

import com.duoinfra.backend.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderImplTest {

    private static final String SECRET = "test-jwt-secret-key-for-unit-test-must-be-long-enough";

    private JwtTokenProviderImpl jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProviderImpl(SECRET, 3600000L, 1209600000L);
    }

    @Test
    @DisplayName("발급한 토큰은 유효하다")
    void generatedToken_isValid() {
        String token = jwtTokenProvider.generateAccessToken(1L, "user@example.com", Role.USER);

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("발급한 토큰에서 userId와 role을 추출할 수 있다")
    void parseToken_returnsClaims() {
        String token = jwtTokenProvider.generateAccessToken(42L, "user@example.com", Role.ADMIN);

        assertThat(jwtTokenProvider.getUserId(token)).isEqualTo(42L);
        assertThat(jwtTokenProvider.getRole(token)).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("변조되었거나 형식이 잘못된 토큰은 유효하지 않다")
    void invalidToken_isNotValid() {
        assertThat(jwtTokenProvider.validateToken("invalid.token.value")).isFalse();
    }

    @Test
    @DisplayName("다른 비밀키로 서명된 토큰은 유효하지 않다")
    void tokenSignedWithDifferentSecret_isNotValid() {
        JwtTokenProviderImpl otherProvider = new JwtTokenProviderImpl("other-secret-key-that-is-also-long-enough", 3600000L, 1209600000L);
        String token = otherProvider.generateAccessToken(1L, "user@example.com", Role.USER);

        assertThat(jwtTokenProvider.validateToken(token)).isFalse();
    }

    @Test
    @DisplayName("발급한 Refresh Token은 유효하고, userId를 추출할 수 있다")
    void generatedRefreshToken_isValidAndContainsUserId() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(7L);

        assertThat(jwtTokenProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtTokenProvider.getUserId(refreshToken)).isEqualTo(7L);
    }
}
