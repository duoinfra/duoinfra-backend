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

    public static ResourceAllocationConflictException lockAcquisitionFailed(Long serverId) {
        return new ResourceAllocationConflictException(
                "서버(id=" + serverId + ")의 자원 락을 획득하지 못했습니다(타임아웃). 잠시 후 다시 시도해주세요."
        );
    }
}