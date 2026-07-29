package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.container.domain.ContainerStatus;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import com.duoinfra.backend.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ContainerPersistenceServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private UserRepository userRepository;

    private ContainerPersistenceService containerPersistenceService;

    @BeforeEach
    void setUp() {
        containerPersistenceService = new ContainerPersistenceService(containerRepository, userRepository);
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

    @Test
    @DisplayName("존재하는 사용자를 조회하면 해당 사용자를 반환한다")
    void findOwner_exists_returnsUser() {
        User owner = userWithId(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        User result = containerPersistenceService.findOwner(1L);

        assertThat(result).isEqualTo(owner);
    }

    @Test
    @DisplayName("존재하지 않는 사용자를 조회하면 예외가 발생한다")
    void findOwner_notExists_throws() {
        given(userRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> containerPersistenceService.findOwner(1L))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("컨테이너를 저장하면 리포지토리에 위임한다")
    void saveContainer_delegatesToRepository() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerRepository.save(container)).willReturn(container);

        Container result = containerPersistenceService.saveContainer(container);

        assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("본인 소유 컨테이너는 접근할 수 있다")
    void getAccessibleContainer_ownedByRequester_returnsContainer() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER);

        assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("ADMIN은 다른 사용자 소유 컨테이너도 접근할 수 있다")
    void getAccessibleContainer_notOwnedButAdmin_returnsContainer() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.getAccessibleContainer(100L, 99L, Role.ADMIN);

        assertThat(result).isEqualTo(container);
    }

    @Test
    @DisplayName("USER가 다른 사용자 소유 컨테이너에 접근하면 예외가 발생한다")
    void getAccessibleContainer_notOwnedAndNotAdmin_throwsNotFound() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        assertThatThrownBy(() -> containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    @DisplayName("존재하지 않는 컨테이너에 접근하면 예외가 발생한다")
    void getAccessibleContainer_notExists_throwsNotFound() {
        given(containerRepository.findById(100L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
    }

    @Test
    @DisplayName("CREATING 상태의 컨테이너도 소유자 권한 체크가 동일하게 적용된다")
    void getAccessibleContainer_creatingStatus_appliesSameOwnershipCheck() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        assertThatThrownBy(() -> containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
        assertThat(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).isEqualTo(container);
    }

    @Test
    @DisplayName("activateContainer는 CREATING 상태의 컨테이너를 RUNNING으로 전이시킨다")
    void activateContainer_transitionsToRunning() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));
        DockerProvisionResult provisionResult = new DockerProvisionResult("docker-1", 10000, "root", "pw");

        Container result = containerPersistenceService.activateContainer(100L, "host", provisionResult);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.RUNNING);
        assertThat(result.getContainerId()).isEqualTo("docker-1");
        assertThat(result.getHost()).isEqualTo("host");
    }

    @Test
    @DisplayName("markCreateFailed는 컨테이너를 CREATE_FAILED로 전이시킨다")
    void markCreateFailed_transitionsToCreateFailed() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.markCreateFailed(100L, ContainerFailureReason.SSH_CONNECTION_FAILED);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.CREATE_FAILED);
        assertThat(result.getFailureReason()).isEqualTo(ContainerFailureReason.SSH_CONNECTION_FAILED);
    }

    @Test
    @DisplayName("markCreateFailedOrphaned은 containerId를 남기고 DB_UPDATE_FAILED로 기록한다")
    void markCreateFailedOrphaned_recordsDockerContainerId() {
        User owner = userWithId(1L);
        Container container = pendingContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.markCreateFailedOrphaned(100L, "docker-orphan");

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.CREATE_FAILED);
        assertThat(result.getContainerId()).isEqualTo("docker-orphan");
        assertThat(result.getFailureReason()).isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);
    }

    @Test
    @DisplayName("beginDeleting은 컨테이너를 DELETING으로 전이시킨다")
    void beginDeleting_transitionsToDeleting() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.beginDeleting(100L);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.DELETING);
    }

    @Test
    @DisplayName("markDeleted는 컨테이너를 DELETED로 전이시킨다")
    void markDeleted_transitionsToDeleted() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        container.beginDeleting();
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.markDeleted(100L);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.DELETED);
    }

    @Test
    @DisplayName("markDeleteFailed는 컨테이너를 DELETE_FAILED로 전이시킨다")
    void markDeleteFailed_transitionsToDeleteFailed() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        container.beginDeleting();
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.markDeleteFailed(100L, ContainerFailureReason.DOCKER_COMMAND_FAILED);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.DELETE_FAILED);
        assertThat(result.getFailureReason()).isEqualTo(ContainerFailureReason.DOCKER_COMMAND_FAILED);
    }

    @Test
    @DisplayName("markDeleteFailedOrphaned은 DB_UPDATE_FAILED로 기록한다")
    void markDeleteFailedOrphaned_recordsDbUpdateFailed() {
        User owner = userWithId(1L);
        Container container = runningContainer(100L, owner);
        container.beginDeleting();
        given(containerRepository.findById(100L)).willReturn(Optional.of(container));

        Container result = containerPersistenceService.markDeleteFailedOrphaned(100L);

        assertThat(result.getStatus()).isEqualTo(ContainerStatus.DELETE_FAILED);
        assertThat(result.getFailureReason()).isEqualTo(ContainerFailureReason.DB_UPDATE_FAILED);
    }
}
