package com.duoinfra.backend.container.domain;

import com.duoinfra.backend.user.domain.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "containers")
public class Container {

    private static final Map<ContainerStatus, Set<ContainerStatus>> ALLOWED_TRANSITIONS = Map.of(
            ContainerStatus.CREATING, Set.of(ContainerStatus.RUNNING, ContainerStatus.CREATE_FAILED),
            ContainerStatus.RUNNING, Set.of(ContainerStatus.DELETING),
            ContainerStatus.CREATE_FAILED, Set.of(ContainerStatus.DELETING),
            ContainerStatus.DELETING, Set.of(ContainerStatus.DELETED, ContainerStatus.DELETE_FAILED),
            ContainerStatus.DELETE_FAILED, Set.of(ContainerStatus.DELETING),
            ContainerStatus.DELETED, Set.of()
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(unique = true)
    private String containerId;

    private String host;

    private Integer sshPort;

    private String sshUsername;

    private String sshPassword;

    @Column(nullable = false)
    private int cpu;

    @Column(nullable = false)
    private int memory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContainerStatus status;

    @Enumerated(EnumType.STRING)
    private ContainerFailureReason failureReason;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Container() {}

    private Container(int cpu, int memory, User owner) {
        this.cpu = cpu;
        this.memory = memory;
        this.owner = owner;
        this.status = ContainerStatus.CREATING;
        this.createdAt = LocalDateTime.now();
    }

    public static Container createPending(int cpu, int memory, User owner) {
        return new Container(cpu, memory, owner);
    }

    private void transitionTo(ContainerStatus target) {
        Set<ContainerStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(status, Set.of());
        if (!allowed.contains(target)) {
            throw new InvalidContainerStatusTransitionException(status, target);
        }
        this.status = target;
    }

    public void activate(String containerId, String host, int sshPort, String sshUsername, String sshPassword) {
        transitionTo(ContainerStatus.RUNNING);
        this.containerId = containerId;
        this.host = host;
        this.sshPort = sshPort;
        this.sshUsername = sshUsername;
        this.sshPassword = sshPassword;
        this.failureReason = null;
    }

    public void markCreateFailed(ContainerFailureReason reason) {
        transitionTo(ContainerStatus.CREATE_FAILED);
        this.failureReason = reason;
    }

    /**
     * Docker 컨테이너 생성 자체는 성공했지만 그 결과를 DB에 반영하는 트랜잭션이 실패한 경우 사용한다.
     * 실물 자원과 DB 상태가 어긋난 "고아 컨테이너"일 수 있으므로, 최소한 어떤 컨테이너가 생성되었는지는
     * 남겨 두어야 #51의 보상 트랜잭션이 이 단서를 바탕으로 정리할 수 있다.
     */
    public void markCreateFailedOrphaned(String dockerContainerId) {
        transitionTo(ContainerStatus.CREATE_FAILED);
        this.containerId = dockerContainerId;
        this.failureReason = ContainerFailureReason.DB_UPDATE_FAILED;
    }

    public void beginDeleting() {
        transitionTo(ContainerStatus.DELETING);
    }

    public void markDeleted() {
        transitionTo(ContainerStatus.DELETED);
        this.failureReason = null;
    }

    public void markDeleteFailed(ContainerFailureReason reason) {
        transitionTo(ContainerStatus.DELETE_FAILED);
        this.failureReason = reason;
    }

    /**
     * Docker 컨테이너 삭제 자체는 성공했지만 그 결과를 DB에 반영하는 트랜잭션이 실패한 경우 사용한다.
     * 이 시점엔 물리 자원은 이미 삭제되었고 DB만 그 사실을 반영하지 못한 상태다.
     */
    public void markDeleteFailedOrphaned() {
        transitionTo(ContainerStatus.DELETE_FAILED);
        this.failureReason = ContainerFailureReason.DB_UPDATE_FAILED;
    }

    public boolean isRetryable() {
        if (status != ContainerStatus.CREATE_FAILED && status != ContainerStatus.DELETE_FAILED) {
            return false;
        }
        return failureReason != null && !failureReason.requiresManualReview();
    }

    public void assertRunning() {
        if (status != ContainerStatus.RUNNING) {
            throw new ContainerNotRunningException(id, status);
        }
    }

    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public User getOwner() { return owner; }
    public String getContainerId() { return containerId; }
    public String getHost() { return host; }
    public Integer getSshPort() { return sshPort; }
    public String getSshUsername() { return sshUsername; }
    public String getSshPassword() { return sshPassword; }
    public int getCpu() { return cpu; }
    public int getMemory() { return memory; }
    public ContainerStatus getStatus() { return status; }
    public ContainerFailureReason getFailureReason() { return failureReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
