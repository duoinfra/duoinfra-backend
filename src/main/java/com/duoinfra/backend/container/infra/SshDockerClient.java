package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.application.DockerProvisionResult;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.stream.Collectors;

@Component
public class SshDockerClient implements DockerClient {

    private static final String SSH_USERNAME = "root";
    private static final int SSH_CONNECT_TIMEOUT_MS = 10000;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final PortAllocator portAllocator;

    public SshDockerClient(
            @Value("${physical-server.host}") String host,
            @Value("${physical-server.port}") int port,
            @Value("${physical-server.username}") String username,
            @Value("${physical-server.password}") String password,
            PortAllocator portAllocator) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.portAllocator = portAllocator;
    }

    @Override
    public DockerProvisionResult provisionContainer(int cpu, int memory) {
        int sshPort = portAllocator.allocate();
        String containerPassword = generatePassword();

        Session session = null;
        try {
            session = createSession();

            String containerId = execCommand(session,
                    String.format(
                            "docker run -d --cpus=%d -m %dm " +
                            "-p %d:22 " +
                            "--restart unless-stopped " +
                            "ubuntu:22.04 " +
                            "/bin/bash -c \"" +
                            "apt-get update -qq && " +
                            "apt-get install -y -qq openssh-server && " +
                            "echo '%s:%s' | chpasswd && " +
                            "sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin yes/' /etc/ssh/sshd_config && " +
                            "mkdir -p /run/sshd && " +
                            "/usr/sbin/sshd -D\"",
                            cpu, memory, sshPort, SSH_USERNAME, containerPassword
                    )
            ).trim();

            return new DockerProvisionResult(containerId, sshPort, SSH_USERNAME, containerPassword);

        } catch (Exception e) {
            throw new ContainerProvisionException("컨테이너 발급 실패: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    private Session createSession() throws Exception {
        JSch jsch = new JSch();
        Session session = jsch.getSession(username, host, port);
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(SSH_CONNECT_TIMEOUT_MS);
        return session;
    }

    private String execCommand(Session session, String command) throws Exception {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        BufferedReader reader = new BufferedReader(new InputStreamReader(channel.getInputStream()));
        channel.connect();

        String output = reader.lines().collect(Collectors.joining("\n"));
        channel.disconnect();
        return output;
    }

    private String generatePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
