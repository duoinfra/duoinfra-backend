package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContainerService {

    private final DockerClient dockerClient;
    private final ContainerRepository containerRepository;
    private final UserRepository userRepository;
    private final String physicalServerHost;

    public ContainerService(DockerClient dockerClient,
                            ContainerRepository containerRepository,
                            UserRepository userRepository,
                            @Value("${physical-server.host}") String physicalServerHost) {
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.userRepository = userRepository;
        this.physicalServerHost = physicalServerHost;
    }

    @Transactional
    public ContainerResult provision(ContainerProvisionCommand command, Long requesterId) {
        User owner = userRepository.findById(requesterId)
                .orElseThrow(() -> new UserNotFoundException(requesterId));

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
        Container saved = containerRepository.save(container);

        return ContainerResult.from(saved);
    }

    @Transactional(readOnly = true)
    public List<ContainerSummary> list(Long requesterId, Role requesterRole) {
        List<Container> containers = requesterRole == Role.ADMIN
                ? containerRepository.findAll()
                : containerRepository.findAllByOwnerId(requesterId);

        return containers.stream().map(ContainerSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public ContainerResult getDetail(Long id, Long requesterId, Role requesterRole) {
        Container container = getAccessibleContainer(id, requesterId, requesterRole);
        return ContainerResult.from(container);
    }

    @Transactional(readOnly = true)
    public ContainerMetrics getMetrics(Long id, Long requesterId, Role requesterRole) {
        Container container = getAccessibleContainer(id, requesterId, requesterRole);
        return dockerClient.getContainerMetrics(container.getContainerId());
    }

    @Transactional
    public void delete(Long id, Long requesterId, Role requesterRole) {
        Container container = getAccessibleContainer(id, requesterId, requesterRole);
        dockerClient.removeContainer(container.getContainerId());
        containerRepository.delete(container);
    }

    private Container getAccessibleContainer(Long id, Long requesterId, Role requesterRole) {
        Container container = containerRepository.findById(id)
                .orElseThrow(() -> new ContainerNotFoundException(id));

        boolean isOwner = container.getOwner().getId().equals(requesterId);
        if (requesterRole != Role.ADMIN && !isOwner) {
            // 다른 사용자의 서버가 존재한다는 사실 자체를 노출하지 않기 위해 403이 아닌 404로 응답한다.
            throw new ContainerNotFoundException(id);
        }
        return container;
    }
}
