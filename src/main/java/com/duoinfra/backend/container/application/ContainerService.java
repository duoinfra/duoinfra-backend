package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
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

        DockerProvisionResult provisioned = dockerClient.provisionContainer(command.cpu(), command.memory());

        Container container = new Container(
                provisioned.containerId(),
                physicalServerHost,
                provisioned.sshPort(),
                provisioned.sshUsername(),
                provisioned.sshPassword(),
                command.cpu(),
                command.memory(),
                owner
        );
        Container saved = containerPersistenceService.saveContainer(container);

        return ContainerResult.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ContainerSummary> list(Long requesterId, Role requesterRole) {
        List<Container> containers = requesterRole == Role.ADMIN
                ? containerRepository.findAll()
                : containerRepository.findAllByOwnerId(requesterId);

        return containers.stream().map(ContainerSummary::from).toList();
    }

    public ContainerResult getDetail(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);
        return ContainerResult.from(container);
    }

    public ContainerMetrics getMetrics(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);
        return dockerClient.getContainerMetrics(container.getContainerId());
    }

    public void delete(Long id, Long requesterId, Role requesterRole) {
        Container container = containerPersistenceService.getAccessibleContainer(id, requesterId, requesterRole);

        dockerClient.removeContainer(container.getContainerId());

        try {
            containerPersistenceService.deleteContainer(container);
        } catch (RuntimeException e) {
            // SSH로 컨테이너 삭제는 이미 성공했으나 DB 레코드 삭제가 실패한 상태.
            // 재시도해도 SSH 쪽은 이미 컨테이너가 없어 자연스럽게 넘어가지만, DB에는 고아 레코드가 남으므로
            // 운영자가 수동으로 확인/정리할 수 있도록 컨테이너 식별 정보를 남기고 예외를 그대로 전파한다.
            log.error("컨테이너 SSH 삭제는 성공했지만 DB 레코드 삭제에 실패했습니다. id={}, containerId={}",
                    id, container.getContainerId(), e);
            throw e;
        }
    }
}
