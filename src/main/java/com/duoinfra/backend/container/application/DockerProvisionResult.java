package com.duoinfra.backend.container.application;

public record DockerProvisionResult(
        String containerId,
        int sshPort,
        String sshUsername,
        String sshPassword
) {}
