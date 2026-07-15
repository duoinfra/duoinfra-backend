package com.duoinfra.backend.user.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @Schema(description = "이메일", example = "user@example.com")
        @NotBlank @Email
        String email,

        @Schema(description = "비밀번호 (8자 이상)", example = "password1234")
        @NotBlank @Size(min = 8)
        String password,

        @Schema(description = "닉네임", example = "duo")
        @NotBlank
        String nickname,

        @Schema(description = "이용약관 동의 여부", example = "true")
        @AssertTrue(message = "이용약관에 동의해야 합니다.")
        boolean termsAgreed
) {}
