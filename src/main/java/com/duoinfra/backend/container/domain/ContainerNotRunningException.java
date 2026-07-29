package com.duoinfra.backend.container.domain;

public class ContainerNotRunningException extends RuntimeException {
    public ContainerNotRunningException(Long id, ContainerStatus status) {
        super("RUNNING 상태의 서버만 가능한 작업입니다. id=" + id + ", status=" + status);
    }
}
