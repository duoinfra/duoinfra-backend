package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.UsageStats;
import io.swagger.v3.oas.annotations.media.Schema;

public record UsageResponse(
        @Schema(description = "평균 CPU 사용률 (%)") double cpu,
        @Schema(description = "평균 메모리 사용률 (%)") double memory,
        @Schema(description = "평균 디스크 사용률 (%)") double disk
) {
    public static UsageResponse from(UsageStats usage) {
        return new UsageResponse(usage.cpu(), usage.memory(), usage.disk());
    }
}
