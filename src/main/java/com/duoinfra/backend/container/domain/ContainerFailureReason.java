package com.duoinfra.backend.container.domain;

/**
 * CREATE_FAILED / DELETE_FAILED로 귀결된 원인을 분류한다.
 * SSH_CONNECTION_FAILED, DOCKER_COMMAND_FAILED는 실물 자원 상태가 DB와 어긋나지 않으므로 단순 재시도로 해결 가능하지만,
 * DB_UPDATE_FAILED는 Docker/SSH 작업 자체는 성공한 뒤 그 결과를 DB에 반영하는 데 실패한 경우라 실물 자원과 DB 상태가
 * 어긋났을 수 있어(고아 자원) 운영자 확인이 필요하다. 실제 정리(보상 트랜잭션)는 #51에서 다룬다.
 */
public enum ContainerFailureReason {
    SSH_CONNECTION_FAILED,
    DOCKER_COMMAND_FAILED,
    DB_UPDATE_FAILED;

    public boolean requiresManualReview() {
        return this == DB_UPDATE_FAILED;
    }
}
