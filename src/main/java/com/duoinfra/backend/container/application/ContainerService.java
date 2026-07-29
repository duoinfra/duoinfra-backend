package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.container.domain.ContainerStatus;
import com.duoinfra.backend.container.infra.SshConnectionException;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContainerService {

    private static final Logger log = LoggerFactory.getLogger(ContainerService.class);

    private final DockerClient dockerClient;
    private final ContainerRepository containerRepository;
    private final ContainerPersistenceService containerPersistenceService;
    private final String physicalServerHost;

    public ContainerService(DockerClient dockerClient,
                            ContainerRepository containerRepository,
                            ContainerPersistenceService containerPersistenceService,
                            @Value("${physical-server.host}") String physicalServerHost) {
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.containerPersistenceService = containerPersistenceService;
        this.physicalServerHost = physicalServerHost;
    }

    public ContainerResult provision(ContainerProvisionCommand command, Long requesterId) {
        User owner = containerPersistenceService.findOwner(requesterId);

        // Docker 작업 전에 CREATING 상태의 row를 먼저 INSERT해 둔다.
        // 이렇게 하면 이후 어느 단계에서 실패하더라도 갱신할 row가 이미 존재하므로
        // 실패 상태(CREATE_FAILED)와 그 원인을 반드시 남길 수 있다.
        Container pending = containerPersistenceService.saveContainer(
                Container.createPending(command.cpu(), command.memory(), owner));
        Long id = pending.getId();

        DockerProvisionResult provisioned;
        try {
            provisioned = dockerClient.provisionContainer(command.cpu(), command.memory());
        } catch (SshConnectionException e) {
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.SSH_CONNECTION_FAILED);
            log.warn("서버 생성 실패(SSH 연결 실패). id={}", id, e);
            throw new ContainerProvisionFailedException(id, ContainerFailureReason.SSH_CONNECTION_FAILED, e);
        } catch (RuntimeException e) {
            containerPersistenceService.markCreateFailed(id, ContainerFailureReason.DOCKER_COMMAND_FAILED);
            log.warn("서버 생성 실패(Docker 명령 실패). id={}", id, e);
            throw new ContainerProvisionFailedException(id, ContainerFailureReason.DOCKER_COMMAND_FAILED, e);
        }

        try {
            Container activated = containerPersistenceService.activateContainer(id, physicalServerHost, provisioned);
            return ContainerResult.from(activated);
        } catch (RuntimeException e) {
            // Docker 컨테이너는 이미 생성되었는데 그 결과를 DB에 반영하지 못했다.
            // 실물 자원과 DB 상태가 어긋난 고아 컨테이너일 수 있으므로, 어떤 컨테이너가 생성되었는지는 반드시 남긴다.
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
