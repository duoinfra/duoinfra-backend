package com.duoinfra.backend.container.application;

public interface DockerClient {
    DockerProvisionResult provisionContainer(int cpu, int memory);
    void removeContainer(String containerId);
    ContainerMetrics getContainerMetrics(String containerId);
}
