package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerStatus;

import java.time.LocalDateTime;

public record ContainerResult(
        Long id,
        String containerId,
        String host,
        Integer sshPort,
        String sshUsername,
        String sshPassword,
        int cpu,
        int memory,
        ContainerStatus status,
        boolean retryable,
        ContainerFailureReason failureReason,
        LocalDateTime createdAt,
        Long ownerId
) {
    public static ContainerResult from(Container container) {
        return new ContainerResult(
                container.getId(),
                container.getContainerId(),
                container.getHost(),
                container.getSshPort(),
                container.getSshUsername(),
                container.getSshPassword(),
                container.getCpu(),
                container.getMemory(),
                container.getStatus(),
                container.isRetryable(),
                container.getFailureReason(),
                container.getCreatedAt(),
                container.getOwner().getId()
        );
    }
}
