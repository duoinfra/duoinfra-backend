package com.duoinfra.backend.container.domain;

public class ContainerNotFoundException extends RuntimeException {
    public ContainerNotFoundException(Long id) {
        super("서버를 찾을 수 없습니다. id=" + id);
    }
}
