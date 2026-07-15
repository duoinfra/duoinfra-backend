package com.duoinfra.backend.user.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    @DisplayName("회원 생성 시 role은 USER, provider는 LOCAL이 기본값이다")
    void create_defaults() {
        User user = new User("user@example.com", "encodedPassword", "duo", true);

        assertThat(user.getEmail()).isEqualTo("user@example.com");
        assertThat(user.getPassword()).isEqualTo("encodedPassword");
        assertThat(user.getNickname()).isEqualTo("duo");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getProviderId()).isNull();
    }

    @Test
    @DisplayName("회원 생성 시 이용약관 동의 정보가 기록된다")
    void create_termsAgreed() {
        User user = new User("user@example.com", "encodedPassword", "duo", true);

        assertThat(user.isTermsAgreed()).isTrue();
        assertThat(user.getTermsAgreedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이용약관에 동의하지 않으면 회원가입할 수 없다")
    void create_termsNotAgreed_throws() {
        assertThatThrownBy(() -> new User("user@example.com", "encodedPassword", "duo", false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
