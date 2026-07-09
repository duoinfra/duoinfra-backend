package com.duoinfra.backend.container.application;

public interface DockerClient {
    DockerProvisionResult provisionContainer(int cpu, int memory);
}
