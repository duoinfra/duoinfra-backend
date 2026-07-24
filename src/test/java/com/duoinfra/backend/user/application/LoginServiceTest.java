package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
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
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private LoginService loginService;

    @BeforeEach
    void setUp() {
        loginService = new LoginService(userRepository, passwordEncoder, jwtTokenProvider, refreshTokenStore);
    }

    @Test
    @DisplayName("이메일/비밀번호가 일치하면 Access Token과 Refresh Token을 발급하고, Refresh Token을 저장한다")
    void login_success() {
        // given
        User user = new User("user@example.com", "encodedPassword", "duo", true);
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1234", "encodedPassword")).willReturn(true);
        given(jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), Role.USER)).willReturn("access-token");
        given(jwtTokenProvider.generateRefreshToken(user.getId())).willReturn("refresh-token");

        // when
        LoginResult result = loginService.login(new LoginCommand("user@example.com", "password1234"));

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.nickname()).isEqualTo("duo");
        then(refreshTokenStore).should().save(user.getId(), "refresh-token");
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 인증 예외가 발생한다")
    void login_emailNotFound_throws() {
        // given
        given(userRepository.findByEmail("unknown@example.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> loginService.login(new LoginCommand("unknown@example.com", "password1234")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 인증 예외가 발생한다")
    void login_wrongPassword_throws() {
        // given
        User user = new User("user@example.com", "encodedPassword", "duo", true);
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPassword", "encodedPassword")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> loginService.login(new LoginCommand("user@example.com", "wrongPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
