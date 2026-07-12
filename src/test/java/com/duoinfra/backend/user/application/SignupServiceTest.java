package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SignupService signupService;

    @BeforeEach
    void setUp() {
        signupService = new SignupService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 성공 시 비밀번호를 해싱하여 저장한다")
    void signup_success() {
        // given
        given(userRepository.existsByEmail("user@example.com")).willReturn(false);
        given(passwordEncoder.encode("password1234")).willReturn("encodedPassword");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        SignupResult result = signupService.signup(new SignupCommand("user@example.com", "password1234", "duo", true));

        // then
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.nickname()).isEqualTo("duo");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("이미 가입된 이메일이면 예외가 발생한다")
    void signup_duplicateEmail_throws() {
        // given
        given(userRepository.existsByEmail("user@example.com")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> signupService.signup(new SignupCommand("user@example.com", "password1234", "duo", true)))
                .isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).save(any(User.class));
    }
}
