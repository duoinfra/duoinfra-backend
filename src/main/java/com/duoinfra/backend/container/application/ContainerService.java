package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.*;
import com.duoinfra.backend.container.infra.SshConnectionException;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.duoinfra.backend.container.domain.ResourceAllocationConflictException;

import java.util.List;

@Service
public class ContainerService {

    private static final Logger log = LoggerFactory.getLogger(ContainerService.class);

    private final DockerClient dockerClient;
    private final ContainerRepository containerRepository;
    private final ContainerPersistenceService containerPersistenceService;
    private final String physicalServerHost;
    private final PhysicalServerService physicalServerService;
    private final Long physicalServerId;

    public ContainerService(DockerClient dockerClient,
                            ContainerRepository containerRepository,
                            ContainerPersistenceService containerPersistenceService,
                            PhysicalServerService physicalServerService,
                            @Value("${physical-server.host}") String physicalServerHost,
                            @Value("${physical-server.id}") Long physicalServerId){
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.containerPersistenceService = containerPersistenceService;
        this.physicalServerService = physicalServerService;
        this.physicalServerHost = physicalServerHost;
        this.physicalServerId = physicalServerId;
    }

    public ContainerResult provision(ContainerProvisionCommand command, Long requesterId) {
        User owner = containerPersistenceService.findOwner(requesterId);

        // Docker 작업 전에 CREATING 상태의 row를 먼저 INSERT해 둔다.
        // 이렇게 하면 이후 어느 단계에서 실패하더라도 갱신할 row가 이미 존재하므로
        // 실패 상태(CREATE_FAILED)와 그 원인을 반드시 남길 수 있다.
        // 1. CREATING 상태로 먼저 저장
        Container pending = containerPersistenceService.saveContainer(
                Container.createPending(command.cpu(), command.memory(), owner));
        Long id = pending.getId();
        // 자원 예약 - 이 부분에서 자원이 부족하면 예외를 던지고 끝.
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                physicalServerService.reserve(physicalServerId, command.cpu(), command.memory());
                break; // 성공하면 반복 종료
            } catch (PessimisticLockingFailureException e) {
                if (attempt == maxRetries) {
                    containerPersistenceService.markCreateFailed(id, ContainerFailureReason.LOCK_ACQUISITION_FAILED);
                    throw ResourceAllocationConflictException.lockTimeout(physicalServerId);
                }
                // 다음 시도 전 짧게 대기 (선택)
            } catch (ResourceAllocationConflictException e) {
                containerPersistenceService.markCreateFailed(id, ContainerFailureReason.INSUFFICIENT_RESOURCE);
                throw e;
            }
        }


        // 2. 실제 Docker(라즈베리파이) 생성 시도
        DockerProvisionResult provisioned;
        try {
            provisioned = dockerClient.provisionContainer(command.cpu(), command.memory());
        } catch (SshConnectionException e) {
            // 자원 반납 시도
            try{
                physicalServerService.release(physicalServerId, command.cpu(), command.memory()); // ★ 실패했으니 예약 반납
            } catch (RuntimeException releaseFailure){
                log.error("CRITICAL: 자원 반납 실패. 수동 확인 필요. id={}", id, releaseFailure);
            }
            // 컨테이너 실패 상태 기록
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.SSH_CONNECTION_FAILED);
            log.warn("서버 생성 실패(SSH 연결 실패). id={}", id, e);
            throw new ContainerProvisionFailedException(id, ContainerFailureReason.SSH_CONNECTION_FAILED, e);
        } catch (RuntimeException e) {
            try {
                physicalServerService.release(physicalServerId, command.cpu(), command.memory());
            } catch (RuntimeException releaseFailure) {
                log.error("CRITICAL: 자원 반납 실패. 수동 확인 필요. id={}", id, releaseFailure);
            }
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.DOCKER_COMMAND_FAILED);
            log.warn("서버 생성 실패(Docker 명령 실패). id={}", id, e);
            throw new ContainerProvisionFailedException(id, ContainerFailureReason.DOCKER_COMMAND_FAILED, e);
        }

        // 3. 성공하면 DB에 최종 확정
        try {
            Container activated = containerPersistenceService.activateContainer(id, physicalServerHost, provisioned);
            return ContainerResult.from(activated);
        } catch (RuntimeException e) {
            // Docker 컨테이너는 이미 생성되었는데 그 결과를 DB에 반영하지 못했다.
            // 실물 자원과 DB 상태가 어긋난 고아 컨테이너일 수 있으므로, 어떤 컨테이너가 생성되었는지는 반드시 남긴다.
            // "고아 컨테이너 처리 단계" -  Docker는 됐으나 DB 반영 실패
            log.error("CRITICAL: Docker 컨테이너({})는 생성되었지만 DB 반영에 실패했습니다. 고아 컨테이너 가능성이 있습니다. id={}",
                    provisioned.containerId(), id, e);
            try {
                containerPersistenceService.markCreateFailedOrphaned(id, provisioned.containerId());
            } catch (RuntimeException fallbackFailure) {
                log.error("CRITICAL: 고아 컨테이너 표시조차 실패했습니다. 수동 확인이 필요합니다. id={}, dockerContainerId={}",
                        id, provisioned.containerId(), fallbackFailure);
            }
            throw new ContainerProvisionFailedException(id, ContainerFailureReason.DB_UPDATE_FAILED, e);
        }
    }

    @Transactional(readOnly = true)
    public List<ContainerSummary> list(Long requesterId, Role requesterRole) {
        List<Container> containers = requesterRole == Role.ADMIN
                ? containerRepository.findAllExcludingStatus(ContainerStatus.DELETED)
                : containerRepository.findAllByOwnerIdExcludingStatus(requesterId, ContainerStatus.DELETED);

        return containers.stream().map(ContainerSummary::from).toList();
    }

    public ContainerResult getDetail(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);
        return ContainerResult.from(container);
    }

    public ContainerMetrics getMetrics(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);
        container.assertRunning();
        return dockerClient.getContainerMetrics(container.getContainerId());
    }

    public void delete(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);

        // 직전 삭제 시도가 "Docker 삭제는 성공했지만 DB 반영에 실패"했던 경우라면, 실물 자원은 이미 없으므로
        // Docker 삭제를 다시 시도하지 않고 DB 상태만 바로잡는다. 그 외의 경우 재시도는 Docker 삭제부터 다시 수행한다.
        boolean dockerAlreadyRemoved = container.getStatus() == ContainerStatus.DELETE_FAILED
                && container.getFailureReason() == ContainerFailureReason.DB_UPDATE_FAILED;

        Container deleting = containerPersistenceService.beginDeleting(id);
        String dockerContainerId = deleting.getContainerId();

        if (dockerContainerId != null && !dockerAlreadyRemoved) {
            try {
                dockerClient.removeContainer(dockerContainerId);
            } catch (SshConnectionException e) {
                containerPersistenceService.markDeleteFailed(id, ContainerFailureReason.SSH_CONNECTION_FAILED);
                log.warn("서버 삭제 실패(SSH 연결 실패). id={}", id, e);
                throw new ContainerDeleteFailedException(id, ContainerFailureReason.SSH_CONNECTION_FAILED, e);
            } catch (RuntimeException e) {
                containerPersistenceService.markDeleteFailed(id, ContainerFailureReason.DOCKER_COMMAND_FAILED);
                log.warn("서버 삭제 실패(Docker 명령 실패). id={}", id, e);
                throw new ContainerDeleteFailedException(id, ContainerFailureReason.DOCKER_COMMAND_FAILED, e);
            }
        }

        try {
            containerPersistenceService.markDeleted(id);
        } catch (RuntimeException e) {
            // Docker 컨테이너는 이미 삭제되었는데(혹은 애초에 존재하지 않았는데) 그 사실을 DB에 반영하지 못했다.
            log.error("CRITICAL: 컨테이너({})는 물리적으로 삭제되었지만 DB 상태 반영에 실패했습니다. id={}",
                    dockerContainerId, id, e);
            try {
                containerPersistenceService.markDeleteFailedOrphaned(id);
            } catch (RuntimeException fallbackFailure) {
                log.error("CRITICAL: 삭제 실패 표시조차 실패했습니다. 수동 확인이 필요합니다. id={}", id, fallbackFailure);
            }
            throw new ContainerDeleteFailedException(id, ContainerFailureReason.DB_UPDATE_FAILED, e);
        }
    }
}
