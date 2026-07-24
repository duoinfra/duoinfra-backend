package com.duoinfra.backend.user.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private TokenBlacklistStore tokenBlacklistStore;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private LogoutService logoutService;

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(jwtTokenProvider, tokenBlacklistStore, refreshTokenStore);
    }

    @Test
    @DisplayName("유효한 Access Token이면 남은 만료 시간과 일치하는 TTL로 블랙리스트에 등록하고 Refresh Token을 삭제한다")
    void logout_success() {
        // given
        long remainingMillis = 60_000L;
        Date expiration = new Date(System.currentTimeMillis() + remainingMillis);
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("access-token")).willReturn(1L);
        given(jwtTokenProvider.getExpiration("access-token")).willReturn(expiration);

        // when
        logoutService.logout(new LogoutCommand("access-token"));

        // then: 테스트 실행 시간 오차를 감안해 TTL이 남은 만료 시간에 근접한지(1초 이내) 확인한다
        then(tokenBlacklistStore).should().blacklist(
                eq("access-token"),
                longThat(actualTtl -> Math.abs(actualTtl - remainingMillis) < 1000L)
        );
        then(refreshTokenStore).should().deleteByUserId(1L);
    }

    @Test
    @DisplayName("서명/만료가 유효하지 않은 Access Token이면 예외가 발생하고 블랙리스트/삭제를 수행하지 않는다")
    void logout_invalidToken_throws() {
        // given
        given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> logoutService.logout(new LogoutCommand("invalid-token")))
                .isInstanceOf(InvalidAccessTokenException.class);

        then(tokenBlacklistStore).shouldHaveNoInteractions();
        then(refreshTokenStore).shouldHaveNoInteractions();
    }
}
