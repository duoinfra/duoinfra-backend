package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.RefreshResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record RefreshResponse(
        @Schema(description = "새로 발급된 Access Token") String accessToken,
        @Schema(description = "토큰 타입", example = "Bearer") String tokenType
) {
    public static RefreshResponse from(RefreshResult result) {
        return new RefreshResponse(result.accessToken(), result.tokenType());
    }
}
