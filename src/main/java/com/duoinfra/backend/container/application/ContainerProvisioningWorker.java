package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.infra.SshConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * ContainerService.provision()이 CREATING 상태 저장과 자원 예약까지 마친 뒤,
 * 실제로 시간이 걸리는 Docker(라즈베리파이) 생성 작업을 이 클래스가 이어받아 처리한다.
 *
 * 같은 클래스(ContainerService) 안에 이 메서드를 두면 self-invocation 문제로
 * @Async가 무시되기 때문에(#26에서 동일한 원리로 ContainerPersistenceService를
 * 분리했던 것과 같은 이유), 반드시 별도 Bean으로 분리해야 한다.
 */
@Component
public class ContainerProvisioningWorker {

    private static final Logger log = LoggerFactory.getLogger(ContainerProvisioningWorker.class);

    private final DockerClient dockerClient;
    private final ContainerPersistenceService containerPersistenceService;
    private final PhysicalServerService physicalServerService;
    private final String physicalServerHost;

    public ContainerProvisioningWorker(DockerClient dockerClient,
                                       ContainerPersistenceService containerPersistenceService,
                                       PhysicalServerService physicalServerService,
                                       @Value("${physical-server.host}") String physicalServerHost) {
        this.dockerClient = dockerClient;
        this.containerPersistenceService = containerPersistenceService;
        this.physicalServerService = physicalServerService;
        this.physicalServerHost = physicalServerHost;
    }

    /**
     * @Async: 이 메서드는 별도 스레드 풀에서 실행된다. 호출자(트랜잭션을 커밋한 스레드)는
     *         이 메서드가 끝날 때까지 기다리지 않는다. 다만 "비동기"일 뿐 "논블로킹"은 아니라서,
     *         이 메서드를 실제로 처리하는 스레드는 Docker/DB 호출이 끝날 때까지 블로킹된다.
     *
     * @TransactionalEventListener(AFTER_COMMIT): ContainerService.provision()의 트랜잭션이
     *         실제로 커밋된 "이후"에만 이 메서드가 실행되도록 보장한다. 커밋 전에 실행되면
     *         이 스레드가 아직 DB에 반영 안 된 CREATING row를 못 찾는 레이스 컨디션이 생길 수 있다.
     */
    @Async("containerProvisioningExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContainerCreatingEvent event) {
        Long id = event.containerId();

        // 실제 Docker(라즈베리파이) 생성 시도
        DockerProvisionResult provisioned;
        try {
            provisioned = dockerClient.provisionContainer(event.cpu(), event.memory());
        } catch (SshConnectionException e) {
            // 자원 반납 시도
            try {
                physicalServerService.release(event.physicalServerId(), event.cpu(), event.memory()); // ★ 실패했으니 예약 반납
            } catch (RuntimeException releaseFailure) {
                log.error("CRITICAL: 자원 반납 실패. 수동 확인 필요. id={}", id, releaseFailure);
            }
            // 컨테이너 실패 상태 기록
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.SSH_CONNECTION_FAILED);
            log.warn("서버 생성 실패(SSH 연결 실패). id={}", id, e);
            // 비동기 메서드라 여기서 throw해도 받아줄 요청 스레드가 이미 없으므로,
            // 로그와 DB 상태 기록만 남기고 종료한다. 사용자는 폴링으로 실패를 확인한다.
            return;
        } catch (RuntimeException e) {
            try {
                physicalServerService.release(event.physicalServerId(), event.cpu(), event.memory());
            } catch (RuntimeException releaseFailure) {
                log.error("CRITICAL: 자원 반납 실패. 수동 확인 필요. id={}", id, releaseFailure);
            }
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.DOCKER_COMMAND_FAILED);
            log.warn("서버 생성 실패(Docker 명령 실패). id={}", id, e);
            return;
        }

        // 성공하면 DB에 최종 확정
        try {
            containerPersistenceService.activateContainer(id, physicalServerHost, provisioned);
        } catch (RuntimeException e) {
            // Docker 컨테이너는 이미 생성되었는데 그 결과를 DB에 반영하지 못했다.
            // 실물 자원과 DB 상태가 어긋난 고아 컨테이너일 수 있으므로, 어떤 컨테이너가 생성되었는지는 반드시 남긴다.
            // "고아 컨테이너 처리 단계" - Docker는 됐으나 DB 반영 실패
            log.error("CRITICAL: Docker 컨테이너({})는 생성되었지만 DB 반영에 실패했습니다. 고아 컨테이너 가능성이 있습니다. id={}",
                    provisioned.containerId(), id, e);
            try {
                containerPersistenceService.markCreateFailedOrphaned(id, provisioned.containerId());
            } catch (RuntimeException fallbackFailure) {
                log.error("CRITICAL: 고아 컨테이너 표시조차 실패했습니다. 수동 확인이 필요합니다. id={}, dockerContainerId={}",
                        id, provisioned.containerId(), fallbackFailure);
            }
            // 이 실패도 throw 대신 로그로만 남긴다. 방치된 CREATING/CREATE_FAILED 상태는
            // 다음 단계에서 만들 재조정 스케줄러가 주기적으로 확인해 복구를 시도한다.
        }
    }
}