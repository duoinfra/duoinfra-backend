package com.duoinfra.backend.container.domain;

public class ResourceAllocationConflictException extends RuntimeException {

    private ResourceAllocationConflictException(String message) {
        super(message);
    }

    public static ResourceAllocationConflictException insufficientResource(Long serverId) {
        return new ResourceAllocationConflictException(
                "서버(id=" + serverId + ")에 요청한 만큼의 자원이 남아있지 않습니다."
        );
    }

    public static ResourceAllocationConflictException lockTimeout(Long serverId) {
        return new ResourceAllocationConflictException(
                "서버(id=" + serverId + ")의 자원 할당 요청이 몰려 처리할 수 없습니다. 잠시 후 다시 시도해주세요."
        );
    }
}