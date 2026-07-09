package com.duoinfra.backend.container.application;

public record ContainerResult(
        String containerId,
        String host,
        int sshPort,
        String sshUsername,
        String sshPassword
) {}
