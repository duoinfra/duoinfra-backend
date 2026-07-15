package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerStatus;

import java.time.LocalDateTime;

public record ContainerSummary(
        Long id,
        String containerId,
        String host,
        int sshPort,
        int cpu,
        int memory,
        ContainerStatus status,
        LocalDateTime createdAt,
        Long ownerId
) {
    public static ContainerSummary from(Container container) {
        return new ContainerSummary(
                container.getId(),
                container.getContainerId(),
                container.getHost(),
                container.getSshPort(),
                container.getCpu(),
                container.getMemory(),
                container.getStatus(),
                container.getCreatedAt(),
                container.getOwner().getId()
        );
    }
}
