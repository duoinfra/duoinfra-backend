package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.application.DockerProvisionResult;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Profile({"local", "test"})
public class DummyDockerClient implements DockerClient {

    private static final String SSH_USERNAME = "root";
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final PortAllocator portAllocator;

    public DummyDockerClient(PortAllocator portAllocator) {
        this.portAllocator = portAllocator;
    }

    @Override
    public DockerProvisionResult provisionContainer(int cpu, int memory) {
        String containerId = "dummy-" + UUID.randomUUID();
        int sshPort = portAllocator.allocate();
        return new DockerProvisionResult(containerId, sshPort, SSH_USERNAME, generatePassword());
    }

    @Override
    public void removeContainer(String containerId) {
        // 실제 물리 서버 호출 없이 즉시 성공 처리
    }

    @Override
    public ContainerMetrics getContainerMetrics(String containerId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new ContainerMetrics(
                random.nextDouble(0, 100),
                random.nextDouble(0, 100),
                random.nextDouble(0, 1024),
                random.nextDouble(0, 1024)
        );
    }

    private String generatePassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
