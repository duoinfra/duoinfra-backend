package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(userRepository, jwtTokenProvider, refreshTokenStore);
    }

    @Test
    @DisplayName("Redis에 저장된 값과 일치하는 Refresh Token이면 새 Access Token을 발급한다")
    void refresh_success() {
        // given
        User user = new User("user@example.com", "encodedPassword", "duo", true);
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(user.getId());
        given(refreshTokenStore.findByUserId(user.getId())).willReturn(Optional.of("refresh-token"));
        given(userRepository.findById(user.getId())).willReturn(Optional.of(user));
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), Role.USER)).willReturn("new-access-token");

        // when
        RefreshResult result = refreshTokenService.refresh(new RefreshCommand("refresh-token"));

        // then
        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("서명/만료가 유효하지 않은 Refresh Token이면 예외가 발생한다")
    void refresh_invalidJwt_throws() {
        // given
        given(jwtTokenProvider.validateToken("invalid-token")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("invalid-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("Redis에 저장된 Refresh Token이 없으면 예외가 발생한다")
    void refresh_notStoredInRedis_throws() {
        // given
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(1L);
        given(refreshTokenStore.findByUserId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("Redis에 저장된 Refresh Token과 값이 다르면(재로그인 등으로 폐기된 경우) 예외가 발생한다")
    void refresh_doesNotMatchStoredToken_throws() {
        // given
        given(jwtTokenProvider.validateToken("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(1L);
        given(refreshTokenStore.findByUserId(1L)).willReturn(Optional.of("new-refresh-token"));

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("old-refresh-token")))
                .isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    @DisplayName("유효한 Refresh Token이지만 사용자가 존재하지 않으면 예외가 발생한다")
    void refresh_userNotFound_throws() {
        // given
        given(jwtTokenProvider.validateToken("refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("refresh-token")).willReturn(999L);
        given(refreshTokenStore.findByUserId(999L)).willReturn(Optional.of("refresh-token"));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> refreshTokenService.refresh(new RefreshCommand("refresh-token")))
                .isInstanceOf(UserNotFoundException.class);
    }
}
