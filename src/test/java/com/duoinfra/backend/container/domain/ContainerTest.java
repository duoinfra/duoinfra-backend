package com.duoinfra.backend.container.domain;

import com.duoinfra.backend.user.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContainerTest {

    private final User owner = new User("owner@test.com", "encoded-pw", "owner", true);

    @Test
    @DisplayName("생성 요청 시 상태는 CREATING이고 접속 정보는 비어있다")
    void createPending_status_creating() {
        Container container = Container.createPending(1, 512, owner);

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.CREATING);
        assertThat(container.getCpu()).isEqualTo(1);
        assertThat(container.getMemory()).isEqualTo(512);
        assertThat(container.getOwner()).isEqualTo(owner);
        assertThat(container.getContainerId()).isNull();
        assertThat(container.getHost()).isNull();
        assertThat(container.getSshPort()).isNull();
        assertThat(container.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("activate 시 CREATING에서 RUNNING으로 전이되고 접속 정보가 채워진다")
    void activate_fromCreating_setsRunningAndConnectionInfo() {
        Container container = Container.createPending(1, 512, owner);

        container.activate("abc123", "192.168.0.1", 10000, "root", "pass");

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(container.getContainerId()).isEqualTo("abc123");
        assertThat(container.getHost()).isEqualTo("192.168.0.1");
        assertThat(container.getSshPort()).isEqualTo(10000);
        assertThat(container.getSshUsername()).isEqualTo("root");
        assertThat(container.getSshPassword()).isEqualTo("pass");
        assertThat(container.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("RUNNING 상태에서 activate를 다시 호출하면 예외가 발생한다")
    void activate_fromRunning_throws() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "192.168.0.1", 10000, "root", "pass");

        assertThatThrownBy(() -> container.activate("abc456", "192.168.0.1", 10001, "root", "pass2"))
                .isInstanceOf(InvalidContainerStatusTransitionException.class);
    }

    @Test
    @DisplayName("markCreateFailed 시 CREATING에서 CREATE_FAILED로 전이되고 재시도 가능하다")
    void markCreateFailed_fromCreating_setsCreateFailedAndRetryable() {
        Container container = Container.createPending(1, 512, owner);

        container.markCreateFailed(ContainerFailureReason.SSH_CONNECTION_FAILED);

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.CREATE_FAILED);
        assertThat(container.getFailureReason()).isEqualTo(ContainerFailureReason.SSH_CONNECTION_FAILED);
        assertThat(container.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("markCreateFailedOrphaned 시 containerId가 남고 재시도 불가능하다")
    void markCreateFailedOrphaned_setsContainerIdAndNotRetryable() {
        Container container = Container.createPending(1, 512, owner);

        container.markCreateFailedOrphaned("docker-orphan-1");

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.CREATE_FAILED);
        assertThat(container.getContainerId()).isEqualTo("docker-orphan-1");
        assertThat(container.getFailureReason()).isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);
        assertThat(container.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("RUNNING, CREATE_FAILED, DELETE_FAILED 상태에서는 beginDeleting으로 DELETING 전이가 가능하다")
    void beginDeleting_fromAllowedStatuses_transitionsToDeleting() {
        Container running = Container.createPending(1, 512, owner);
        running.activate("abc123", "host", 10000, "root", "pass");
        running.beginDeleting();
        assertThat(running.getStatus()).isEqualTo(ContainerStatus.DELETING);

        Container createFailed = Container.createPending(1, 512, owner);
        createFailed.markCreateFailed(ContainerFailureReason.SSH_CONNECTION_FAILED);
        createFailed.beginDeleting();
        assertThat(createFailed.getStatus()).isEqualTo(ContainerStatus.DELETING);

        Container deleteFailed = Container.createPending(1, 512, owner);
        deleteFailed.activate("abc123", "host", 10000, "root", "pass");
        deleteFailed.beginDeleting();
        deleteFailed.markDeleteFailed(ContainerFailureReason.DOCKER_COMMAND_FAILED);
        deleteFailed.beginDeleting();
        assertThat(deleteFailed.getStatus()).isEqualTo(ContainerStatus.DELETING);
    }

    @Test
    @DisplayName("CREATING 상태에서 beginDeleting을 호출하면 예외가 발생한다")
    void beginDeleting_fromCreating_throws() {
        Container container = Container.createPending(1, 512, owner);

        assertThatThrownBy(container::beginDeleting)
                .isInstanceOf(InvalidContainerStatusTransitionException.class);
    }

    @Test
    @DisplayName("DELETED 상태에서 beginDeleting을 호출하면 예외가 발생한다")
    void beginDeleting_fromDeleted_throws() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "host", 10000, "root", "pass");
        container.beginDeleting();
        container.markDeleted();

        assertThatThrownBy(container::beginDeleting)
                .isInstanceOf(InvalidContainerStatusTransitionException.class);
    }

    @Test
    @DisplayName("markDeleted 시 DELETING에서 DELETED로 전이되고 failureReason이 지워진다")
    void markDeleted_fromDeleting_clearsFailureReason() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "host", 10000, "root", "pass");
        container.beginDeleting();

        container.markDeleted();

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.DELETED);
        assertThat(container.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("markDeleteFailed 시 DELETING에서 DELETE_FAILED로 전이되고 원인에 따라 재시도 가능 여부가 결정된다")
    void markDeleteFailed_fromDeleting_setsReasonAndRetryable() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "host", 10000, "root", "pass");
        container.beginDeleting();

        container.markDeleteFailed(ContainerFailureReason.DOCKER_COMMAND_FAILED);

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.DELETE_FAILED);
        assertThat(container.getFailureReason()).isEqualTo(ContainerFailureReason.DOCKER_COMMAND_FAILED);
        assertThat(container.isRetryable()).isTrue();
    }

    @Test
    @DisplayName("markDeleteFailedOrphaned 시 DB_UPDATE_FAILED로 기록되고 재시도 불가능하다")
    void markDeleteFailedOrphaned_setsDbUpdateFailedAndNotRetryable() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "host", 10000, "root", "pass");
        container.beginDeleting();

        container.markDeleteFailedOrphaned();

        assertThat(container.getStatus()).isEqualTo(ContainerStatus.DELETE_FAILED);
        assertThat(container.getFailureReason()).isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);
        assertThat(container.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("실패 상태가 아니면 isRetryable은 항상 false다")
    void isRetryable_nonFailedStatus_isFalse() {
        Container container = Container.createPending(1, 512, owner);
        assertThat(container.isRetryable()).isFalse();

        container.activate("abc123", "host", 10000, "root", "pass");
        assertThat(container.isRetryable()).isFalse();
    }

    @Test
    @DisplayName("assertRunning은 RUNNING이 아니면 예외를 던진다")
    void assertRunning_notRunning_throws() {
        Container container = Container.createPending(1, 512, owner);

        assertThatThrownBy(container::assertRunning)
                .isInstanceOf(ContainerNotRunningException.class);
    }

    @Test
    @DisplayName("assertRunning은 RUNNING이면 예외를 던지지 않는다")
    void assertRunning_running_doesNotThrow() {
        Container container = Container.createPending(1, 512, owner);
        container.activate("abc123", "host", 10000, "root", "pass");

        container.assertRunning();
    }
}
