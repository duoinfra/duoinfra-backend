package com.duoinfra.backend.user.presentation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @Schema(description = "Refresh Token") @NotBlank String refreshToken
) {
}
