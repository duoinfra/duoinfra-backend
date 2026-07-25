package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.application.DockerProvisionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Local-only Docker adapter. It talks to the developer's Docker daemon without
 * involving SSH, while keeping the same DockerClient port used in production.
 */
@Component
@Profile("local-docker")
public class LocalDockerClient implements DockerClient {

    private static final String LABEL_KEY = "duoinfra.managed";
    private static final String LABEL_VALUE = "local-docker";
    private static final String SSH_USERNAME = "root";
    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PortAllocator portAllocator;
    private final ObjectMapper objectMapper;

    public LocalDockerClient(PortAllocator portAllocator, ObjectMapper objectMapper) {
        this.portAllocator = portAllocator;
        this.objectMapper = objectMapper;
    }

    @Override
    public DockerProvisionResult provisionContainer(int cpu, int memory) {
        int sshPort = portAllocator.allocate();
        String containerPassword = generatePassword();
        String containerName = "duoinfra-local-" + UUID.randomUUID();
        String bootstrap = "apt-get update -qq && "
                + "apt-get install -y -qq openssh-server && "
                + "echo '" + SSH_USERNAME + ":" + containerPassword + "' | chpasswd && "
                + "sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config && "
                + "mkdir -p /run/sshd && /usr/sbin/sshd -D";

        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "-d",
                "--name", containerName,
                "--label", LABEL_KEY + "=" + LABEL_VALUE,
                "--cpus", String.valueOf(cpu),
                "--memory", memory + "m",
                "-p", sshPort + ":22",
                "--restart", "unless-stopped",
                "ubuntu:22.04",
                "/bin/bash", "-c", bootstrap
        ));

        CommandResult result = execute(command);
        return new DockerProvisionResult(result.stdout().trim(), sshPort, SSH_USERNAME, containerPassword);
    }

    @Override
    public void removeContainer(String containerId) {
        execute(List.of("docker", "rm", "-f", containerId));
    }

    @Override
    public ContainerMetrics getContainerMetrics(String containerId) {
        CommandResult result = execute(List.of(
                "docker", "stats", containerId, "--no-stream", "--format", "{{json .}}"
        ));

        try {
            JsonNode node = objectMapper.readTree(result.stdout().trim());
            double[] network = parseNetworkIo(node.path("NetIO").asText("0B / 0B"));
            return new ContainerMetrics(
                    parsePercent(node.path("CPUPerc").asText("0%")),
                    parsePercent(node.path("MemPerc").asText("0%")),
                    0.0,
                    network[0],
                    network[1]
            );
        } catch (IOException | RuntimeException e) {
            throw new ContainerProvisionException("로컬 Docker 메트릭 파싱 실패: " + e.getMessage(), e);
        }
    }

    private CommandResult execute(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ContainerProvisionException("로컬 Docker 명령 실패: " + stderr.trim(), null);
            }
            return new CommandResult(stdout, stderr);
        } catch (IOException e) {
            throw new ContainerProvisionException("Docker CLI를 실행할 수 없습니다. Docker Desktop이 실행 중인지 확인하세요.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerProvisionException("로컬 Docker 명령이 중단되었습니다.", e);
        }
    }

    private double parsePercent(String value) {
        return Double.parseDouble(value.replace("%", "").trim());
    }

    private double[] parseNetworkIo(String value) {
        String[] parts = value.split("/");
        if (parts.length != 2) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{parseBytes(parts[0]), parseBytes(parts[1])};
    }

    private double parseBytes(String value) {
        String normalized = value.trim();
        if (normalized.endsWith("GB")) return Double.parseDouble(normalized.replace("GB", "").trim()) * 1024;
        if (normalized.endsWith("MB")) return Double.parseDouble(normalized.replace("MB", "").trim());
        if (normalized.endsWith("kB")) return Double.parseDouble(normalized.replace("kB", "").trim()) / 1024;
        return Double.parseDouble(normalized.replace("B", "").trim()) / (1024 * 1024);
    }

    private String generatePassword() {
        StringBuilder password = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            password.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    private record CommandResult(String stdout, String stderr) {
    }
}
