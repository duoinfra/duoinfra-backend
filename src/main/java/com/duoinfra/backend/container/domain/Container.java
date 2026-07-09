package com.duoinfra.backend.container.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "containers")
public class Container {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String containerId;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int sshPort;

    @Column(nullable = false)
    private String sshUsername;

    @Column(nullable = false)
    private String sshPassword;

    @Column(nullable = false)
    private int cpu;

    @Column(nullable = false)
    private int memory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Container() {}

    public Container(String containerId, String host, int sshPort, String sshUsername,
                     String sshPassword, int cpu, int memory) {
        this.containerId = containerId;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.sshPassword = sshPassword;
        this.cpu = cpu;
        this.memory = memory;
        this.status = ContainerStatus.RUNNING;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getContainerId() { return containerId; }
    public String getHost() { return host; }
    public int getSshPort() { return sshPort; }
    public String getSshUsername() { return sshUsername; }
    public String getSshPassword() { return sshPassword; }
    public int getCpu() { return cpu; }
    public int getMemory() { return memory; }
    public ContainerStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
