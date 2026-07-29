package com.duoinfra.backend.container.domain;

public class InvalidContainerStatusTransitionException extends RuntimeException {
    public InvalidContainerStatusTransitionException(ContainerStatus from, ContainerStatus to) {
        super("허용되지 않는 상태 전이입니다. from=" + from + ", to=" + to);
    }
}
