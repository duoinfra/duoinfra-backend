package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.SignupResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        @Schema(description = "사용자 ID") Long id,
        @Schema(description = "이메일") String email,
        @Schema(description = "닉네임") String nickname
) {
    public static SignupResponse from(SignupResult result) {
        return new SignupResponse(result.id(), result.email(), result.nickname());
    }
}
