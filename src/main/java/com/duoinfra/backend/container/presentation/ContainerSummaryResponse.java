package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerSummary;
import com.duoinfra.backend.container.domain.ContainerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ContainerSummaryResponse(
        @Schema(description = "서버 ID") Long id,
        @Schema(description = "컨테이너 ID") String containerId,
        @Schema(description = "접속 호스트") String host,
        @Schema(description = "SSH 포트") int sshPort,
        @Schema(description = "CPU 코어 수") int cpu,
        @Schema(description = "메모리 (MB)") int memory,
        @Schema(description = "상태") ContainerStatus status,
        @Schema(description = "생성 시각") LocalDateTime createdAt,
        @Schema(description = "소유자 사용자 ID") Long ownerId
) {
    public static ContainerSummaryResponse from(ContainerSummary summary) {
        return new ContainerSummaryResponse(
                summary.id(),
                summary.containerId(),
                summary.host(),
                summary.sshPort(),
                summary.cpu(),
                summary.memory(),
                summary.status(),
                summary.createdAt(),
                summary.ownerId()
        );
    }
}
