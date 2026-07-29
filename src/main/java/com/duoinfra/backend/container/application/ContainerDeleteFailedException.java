package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.ContainerFailureReason;

public class ContainerDeleteFailedException extends RuntimeException {

    private final Long containerId;
    private final ContainerFailureReason reason;

    public ContainerDeleteFailedException(Long containerId, ContainerFailureReason reason, Throwable cause) {
        super("서버 삭제에 실패했습니다. id=" + containerId + ", reason=" + reason, cause);
        this.containerId = containerId;
        this.reason = reason;
    }

    public Long getContainerId() { return containerId; }
    public ContainerFailureReason getReason() { return reason; }
}
