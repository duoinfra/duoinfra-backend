package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerNotRunningException;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.container.domain.ContainerStatus;
import com.duoinfra.backend.container.domain.InvalidContainerStatusTransitionException;
import com.duoinfra.backend.container.infra.SshConnectionException;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private ContainerPersistenceService containerPersistenceService;

    private ContainerService containerService;

    @BeforeEach
    void setUp() {
        containerService = new ContainerService(dockerClient, containerRepository, containerPersistenceService, "220.117.221.158");
    }

    private User userWithId(Long id) {
        User user = new User(id + "@test.com", "encoded-pw", "user" + id, true);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Container pendingContainer(Long id, User owner) {
        Container container = Container.createPending(1, 512, owner);
        ReflectionTestUtils.setField(container, "id", id);
        return container;
    }

    private Container runningContainer(Long id, User owner) {
        Container container = pendingContainer(id, owner);
        container.activate("docker-" + id, "220.117.221.158", 10000, "root", "password1234");
        return container;
    }

    private Container deletingContainer(Long id, User owner) {
        Container container = runningContainer(id, owner);
        container.beginDeleting();
        return container;
    }

    private Container createFailedContainer(Long id, User owner, ContainerFailureReason reason) {
        Container container = pendingContainer(id, owner);
        container.markCreateFailed(reason);
        return container;
    }

    private Container deleteFailedOrphanContainer(Long id, User owner) {
        Container container = runningContainer(id, owner);
        container.beginDeleting();
        container.markDeleteFailedOrphaned();
        return container;
    }

    // ---------- provision ----------

    @Test
    @DisplayName("컨테이너 발급 성공 시 접속 정보를 반환한다")
    void provision_success() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerPersistenceService.activateContainer(10L, "220.117.221.158", provisionResult))
                .willAnswer(inv -> {
                    Container container = pendingContainer(10L, owner);
                    container.activate("abc123", "220.117.221.158", 10000, "root", "password1234");
                    return container;
                });

        ContainerResult result = containerService.provision(new ContainerProvisionCommand(1, 512), 1L);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.containerId()).isEqualTo("abc123");
        assertThat(result.host()).isEqualTo("220.117.221.158");
        assertThat(result.sshPort()).isEqualTo(10000);
        assertThat(result.status()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(result.ownerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("컨테이너 발급 시 CREATING row를 저장한 뒤 SSH 프로비저닝, 그 다음 활성화 순으로 진행된다")
    void provision_savesPendingRowBeforeDockerAndActivatesAfter() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerPersistenceService.activateContainer(10L, "220.117.221.158", provisionResult))
                .willReturn(runningContainer(10L, owner));

        containerService.provision(new ContainerProvisionCommand(1, 512), 1L);

        InOrder inOrder = inOrder(dockerClient, containerPersistenceService);
        inOrder.verify(containerPersistenceService).saveContainer(any(Container.class));
        inOrder.verify(dockerClient).provisionContainer(1, 512);
        inOrder.verify(containerPersistenceService).activateContainer(10L, "220.117.221.158", provisionResult);
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 발급을 시도하면 예외가 발생하고 SSH 프로비저닝은 호출되지 않는다")
    void provision_userNotFound_throws() {
        given(containerPersistenceService.findOwner(1L)).willThrow(new UserNotFoundException(1L));

        assertThatThrownBy(() -> containerService.provision(new ContainerProvisionCommand(1, 512), 1L))
                .isInstanceOf(UserNotFoundException.class);

        verify(dockerClient, never()).provisionContainer(anyInt(), anyInt());
        verify(containerPersistenceService, never()).saveContainer(any());
    }

    @Test
    @DisplayName("SSH 연결 실패 시 CREATE_FAILED(재시도 가능)로 남기고 예외를 던진다")
    void provision_sshConnectionFailure_marksCreateFailedRetryable() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });
        SshConnectionException sshFailure = new SshConnectionException("연결 실패", null);
        given(dockerClient.provisionContainer(1, 512)).willThrow(sshFailure);

        assertThatThrownBy(() -> containerService.provision(new ContainerProvisionCommand(1, 512), 1L))
                .isInstanceOf(ContainerProvisionFailedException.class)
                .extracting(e -> ((ContainerProvisionFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.SSH_CONNECTION_FAILED);

        verify(containerPersistenceService).markCreateFailed(10L, ContainerFailureReason.SSH_CONNECTION_FAILED);
        verify(containerPersistenceService, never()).activateContainer(any(), any(), any());
    }

    @Test
    @DisplayName("Docker 명령 실패 시 CREATE_FAILED(재시도 가능)로 남기고 예외를 던진다")
    void provision_dockerCommandFailure_marksCreateFailedRetryable() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });
        given(dockerClient.provisionContainer(1, 512)).willThrow(new RuntimeException("docker run 실패"));

        assertThatThrownBy(() -> containerService.provision(new ContainerProvisionCommand(1, 512), 1L))
                .isInstanceOf(ContainerProvisionFailedException.class)
                .extracting(e -> ((ContainerProvisionFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.DOCKER_COMMAND_FAILED);

        verify(containerPersistenceService).markCreateFailed(10L, ContainerFailureReason.DOCKER_COMMAND_FAILED);
    }

    @Test
    @DisplayName("Docker 성공 후 DB 반영이 실패하면 고아 컨테이너로 표시하고 예외를 던진다")
    void provision_dockerSucceedsButDbActivateFails_marksOrphaned() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerPersistenceService.activateContainer(10L, "220.117.221.158", provisionResult))
                .willThrow(new RuntimeException("DB 연결 끊김"));

        assertThatThrownBy(() -> containerService.provision(new ContainerProvisionCommand(1, 512), 1L))
                .isInstanceOf(ContainerProvisionFailedException.class)
                .extracting(e -> ((ContainerProvisionFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);

        verify(containerPersistenceService).markCreateFailedOrphaned(10L, "abc123");
    }

    // ---------- list ----------

    @Test
    @DisplayName("USER는 본인 소유의 삭제되지 않은 컨테이너 목록만 조회한다")
    void list_asUser_returnsOwnNonDeletedContainersOnly() {
        User owner = userWithId(1L);
        given(containerRepository.findAllByOwnerIdExcludingStatus(1L, ContainerStatus.DELETED))
                .willReturn(List.of(runningContainer(100L, owner)));

        List<ContainerSummary> result = containerService.list(1L, Role.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerId()).isEqualTo(1L);
        verify(containerRepository, never()).findAllExcludingStatus(any());
    }

    @Test
    @DisplayName("ADMIN은 삭제되지 않은 전체 사용자의 컨테이너 목록을 조회한다")
    void list_asAdmin_returnsAllNonDeletedContainers() {
        User owner1 = userWithId(1L);
        User owner2 = userWithId(2L);
        given(containerRepository.findAllExcludingStatus(ContainerStatus.DELETED))
                .willReturn(List.of(runningContainer(100L, owner1), runningContainer(200L, owner2)));

        List<ContainerSummary> result = containerService.list(99L, Role.ADMIN);

        assertThat(result).hasSize(2);
        verify(containerRepository, never()).findAllByOwnerIdExcludingStatus(any(), any());
    }

    // ---------- getDetail ----------

    @Test
    @DisplayName("본인 소유 컨테이너는 상세 조회할 수 있다")
    void getDetail_ownedByRequester_returnsDetail() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);

        ContainerResult result = containerService.getDetail(100L, 1L, Role.USER);

        assertThat(result.id()).isEqualTo(100L);
    }

    @Test
    @DisplayName("존재하지 않거나 접근 권한이 없는 컨테이너를 조회하면 예외가 발생한다")
    void getDetail_notAccessible_throwsNotFound() {
        given(containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .willThrow(new ContainerNotFoundException(100L));

        assertThatThrownBy(() -> containerService.getDetail(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    // ---------- delete ----------

    @Test
    @DisplayName("본인 소유 컨테이너는 삭제할 수 있다")
    void delete_ownedByRequester_deletesContainer() {
        User owner = userWithId(1L);
        Container running = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(running);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));

        containerService.delete(100L, 1L, Role.USER);

        verify(dockerClient).removeContainer("docker-100");
        verify(containerPersistenceService).markDeleted(100L);
    }

    @Test
    @DisplayName("삭제는 beginDeleting -> Docker 삭제 -> markDeleted 순서로 진행된다")
    void delete_ordersDbTransitionThenDockerThenFinalDbUpdate() {
        User owner = userWithId(1L);
        Container running = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(running);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));

        containerService.delete(100L, 1L, Role.USER);

        InOrder inOrder = inOrder(containerPersistenceService, dockerClient);
        inOrder.verify(containerPersistenceService).beginDeleting(100L);
        inOrder.verify(dockerClient).removeContainer("docker-100");
        inOrder.verify(containerPersistenceService).markDeleted(100L);
    }

    @Test
    @DisplayName("존재하지 않거나 접근 권한이 없는 컨테이너를 삭제하려 하면 예외가 발생하고 SSH 호출은 일어나지 않는다")
    void delete_notAccessible_throwsNotFound() {
        given(containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .willThrow(new ContainerNotFoundException(100L));

        assertThatThrownBy(() -> containerService.delete(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
        verify(dockerClient, never()).removeContainer(any());
        verify(containerPersistenceService, never()).beginDeleting(any());
    }

    @Test
    @DisplayName("이미 삭제 중이거나 삭제 완료된 컨테이너를 다시 삭제하려 하면 전이 예외가 발생하고 Docker 호출은 일어나지 않는다")
    void delete_invalidTransition_throwsAndSkipsDocker() {
        User owner = userWithId(1L);
        Container deleting = deletingContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(deleting);
        given(containerPersistenceService.beginDeleting(100L))
                .willThrow(new InvalidContainerStatusTransitionException(ContainerStatus.DELETING, ContainerStatus.DELETING));

        assertThatThrownBy(() -> containerService.delete(100L, 1L, Role.USER))
                .isInstanceOf(InvalidContainerStatusTransitionException.class);

        verify(dockerClient, never()).removeContainer(any());
    }

    @Test
    @DisplayName("삭제 중 SSH 연결 실패 시 DELETE_FAILED(재시도 가능)로 남긴다")
    void delete_sshConnectionFailure_marksDeleteFailedRetryable() {
        User owner = userWithId(1L);
        Container running = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(running);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));
        doThrow(new SshConnectionException("연결 실패", null)).when(dockerClient).removeContainer("docker-100");

        assertThatThrownBy(() -> containerService.delete(100L, 1L, Role.USER))
                .isInstanceOf(ContainerDeleteFailedException.class)
                .extracting(e -> ((ContainerDeleteFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.SSH_CONNECTION_FAILED);

        verify(containerPersistenceService).markDeleteFailed(100L, ContainerFailureReason.SSH_CONNECTION_FAILED);
        verify(containerPersistenceService, never()).markDeleted(any());
    }

    @Test
    @DisplayName("삭제 중 Docker 명령 실패 시 DELETE_FAILED(재시도 가능)로 남긴다")
    void delete_dockerCommandFailure_marksDeleteFailedRetryable() {
        User owner = userWithId(1L);
        Container running = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(running);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));
        doThrow(new RuntimeException("docker rm 실패")).when(dockerClient).removeContainer("docker-100");

        assertThatThrownBy(() -> containerService.delete(100L, 1L, Role.USER))
                .isInstanceOf(ContainerDeleteFailedException.class)
                .extracting(e -> ((ContainerDeleteFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.DOCKER_COMMAND_FAILED);

        verify(containerPersistenceService).markDeleteFailed(100L, ContainerFailureReason.DOCKER_COMMAND_FAILED);
    }

    @Test
    @DisplayName("Docker 삭제 성공 후 DB 반영이 실패하면 고아 상태로 표시한다")
    void delete_dockerSucceedsButMarkDeletedFails_marksOrphaned() {
        User owner = userWithId(1L);
        Container running = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(running);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));
        doThrow(new RuntimeException("DB 연결 끊김")).when(containerPersistenceService).markDeleted(100L);

        assertThatThrownBy(() -> containerService.delete(100L, 1L, Role.USER))
                .isInstanceOf(ContainerDeleteFailedException.class)
                .extracting(e -> ((ContainerDeleteFailedException) e).getReason())
                .isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);

        verify(dockerClient).removeContainer("docker-100");
        verify(containerPersistenceService).markDeleteFailedOrphaned(100L);
    }

    @Test
    @DisplayName("Docker 컨테이너가 없던 CREATE_FAILED 컨테이너를 삭제하면 Docker 호출 없이 바로 삭제 완료된다")
    void delete_createFailedWithoutDockerContainer_skipsDockerCall() {
        User owner = userWithId(1L);
        Container createFailed = createFailedContainer(100L, owner, ContainerFailureReason.SSH_CONNECTION_FAILED);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(createFailed);
        Container deleting = createFailedContainer(100L, owner, ContainerFailureReason.SSH_CONNECTION_FAILED);
        deleting.beginDeleting();
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deleting);

        containerService.delete(100L, 1L, Role.USER);

        verify(dockerClient, never()).removeContainer(any());
        verify(containerPersistenceService).markDeleted(100L);
    }

    @Test
    @DisplayName("Docker 삭제는 이미 성공했던 DELETE_FAILED 컨테이너를 재시도하면 Docker 호출을 생략한다")
    void delete_retryAfterOrphanedDeleteFailure_skipsDockerCall() {
        User owner = userWithId(1L);
        Container orphanFailed = deleteFailedOrphanContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(orphanFailed);
        given(containerPersistenceService.beginDeleting(100L)).willReturn(deletingContainer(100L, owner));

        containerService.delete(100L, 1L, Role.USER);

        verify(dockerClient, never()).removeContainer(any());
        verify(containerPersistenceService).markDeleted(100L);
    }

    // ---------- getMetrics ----------

    @Test
    @DisplayName("본인 소유 컨테이너의 메트릭을 조회할 수 있다")
    void getMetrics_ownedByRequester_returnsMetrics() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);
        ContainerMetrics metrics = new ContainerMetrics(10.0, 20.0, 30.0, 40.0, 50.0);
        given(dockerClient.getContainerMetrics("docker-100")).willReturn(metrics);

        ContainerMetrics result = containerService.getMetrics(100L, 1L, Role.USER);

        assertThat(result).isEqualTo(metrics);
    }

    @Test
    @DisplayName("존재하지 않거나 접근 권한이 없는 컨테이너의 메트릭을 조회하려 하면 예외가 발생하고 SSH 호출은 일어나지 않는다")
    void getMetrics_notAccessible_throwsNotFound() {
        given(containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .willThrow(new ContainerNotFoundException(100L));

        assertThatThrownBy(() -> containerService.getMetrics(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
        verify(dockerClient, never()).getContainerMetrics(any());
    }

    @Test
    @DisplayName("RUNNING 상태가 아닌 컨테이너의 메트릭을 조회하려 하면 예외가 발생하고 SSH 호출은 일어나지 않는다")
    void getMetrics_notRunning_throws() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);

        assertThatThrownBy(() -> containerService.getMetrics(100L, 1L, Role.USER))
                .isInstanceOf(ContainerNotRunningException.class);
        verify(dockerClient, never()).getContainerMetrics(any());
    }
}
