package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerMetrics;
import io.swagger.v3.oas.annotations.media.Schema;

public record ContainerMetricsResponse(
        @Schema(description = "CPU 사용률 (%)") double cpu,
        @Schema(description = "메모리 사용률 (%)") double memory,
        @Schema(description = "디스크 사용률 (%)") double disk,
        @Schema(description = "네트워크 수신량") double networkIn,
        @Schema(description = "네트워크 송신량") double networkOut
) {
    public static ContainerMetricsResponse from(ContainerMetrics metrics) {
        return new ContainerMetricsResponse(metrics.cpu(), metrics.memory(), metrics.disk(), metrics.networkIn(), metrics.networkOut());
    }
}
