package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContainerService {

    private final DockerClient dockerClient;
    private final ContainerRepository containerRepository;
    private final String physicalServerHost;

    public ContainerService(DockerClient dockerClient,
                            ContainerRepository containerRepository,
                            @Value("${physical-server.host}") String physicalServerHost) {
        this.dockerClient = dockerClient;
        this.containerRepository = containerRepository;
        this.physicalServerHost = physicalServerHost;
    }

    @Transactional
    public ContainerResult provision(ContainerProvisionCommand command) {
        DockerProvisionResult provisioned = dockerClient.provisionContainer(command.cpu(), command.memory());

        Container container = new Container(
                provisioned.containerId(),
                physicalServerHost,
                provisioned.sshPort(),
                provisioned.sshUsername(),
                provisioned.sshPassword(),
                command.cpu(),
                command.memory()
        );
        containerRepository.save(container);

        return new ContainerResult(
                provisioned.containerId(),
                physicalServerHost,
                provisioned.sshPort(),
                provisioned.sshUsername(),
                provisioned.sshPassword()
        );
    }
}
