package com.duoinfra.backend.container.application;

public record ContainerCreatingEvent(
        Long containerId,
        Long physicalServerId,
        int cpu,
        int memory
) { }