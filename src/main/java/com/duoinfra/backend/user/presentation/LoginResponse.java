package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.LoginResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "Access Token") String accessToken,
        @Schema(description = "Refresh Token") String refreshToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType,
        @Schema(description = "이메일") String email,
        @Schema(description = "닉네임") String nickname
) {
    public static LoginResponse from(LoginResult result) {
        return new LoginResponse(result.accessToken(), result.refreshToken(), result.tokenType(), result.email(), result.nickname());
    }
}
