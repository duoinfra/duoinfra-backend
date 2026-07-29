package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerResult;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerStatus;
import com.duoinfra.backend.user.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ContainerDetailResponse(
        @Schema(description = "서버 ID") Long id,
        @Schema(description = "컨테이너 ID") String containerId,
        @Schema(description = "접속 호스트") String host,
        @Schema(description = "SSH 포트") Integer sshPort,
        @Schema(description = "SSH 유저명") String sshUsername,
        @Schema(description = "SSH 비밀번호") String sshPassword,
        @Schema(description = "CPU 코어 수") int cpu,
        @Schema(description = "메모리 (MB)") int memory,
        @Schema(description = "상태") ContainerStatus status,
        @Schema(description = "CREATE_FAILED/DELETE_FAILED 상태일 때 재시도로 해결 가능한지 여부") boolean retryable,
        @Schema(description = "실패 원인 (ADMIN에게만 노출)") ContainerFailureReason failureReason,
        @Schema(description = "생성 시각") LocalDateTime createdAt,
        @Schema(description = "소유자 사용자 ID") Long ownerId
) {
    public static ContainerDetailResponse from(ContainerResult result, Role requesterRole) {
        return new ContainerDetailResponse(
                result.id(),
                result.containerId(),
                result.host(),
                result.sshPort(),
                result.sshUsername(),
                result.sshPassword(),
                result.cpu(),
                result.memory(),
                result.status(),
                result.retryable(),
                requesterRole == Role.ADMIN ? result.failureReason() : null,
                result.createdAt(),
                result.ownerId()
        );
    }
}
