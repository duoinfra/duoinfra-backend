package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerRepository;
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

    private Container containerOwnedBy(Long containerId, User owner) {
        Container container = new Container("docker-" + containerId, "220.117.221.158", 10000,
                "root", "password1234", 1, 512, owner);
        ReflectionTestUtils.setField(container, "id", containerId);
        return container;
    }

    @Test
    @DisplayName("컨테이너 발급 성공 시 접속 정보를 반환한다")
    void provision_success() {
        // given
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> {
            Container container = inv.getArgument(0);
            ReflectionTestUtils.setField(container, "id", 10L);
            return container;
        });

        // when
        ContainerResult result = containerService.provision(new ContainerProvisionCommand(1, 512), 1L);

        // then
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.containerId()).isEqualTo("abc123");
        assertThat(result.host()).isEqualTo("220.117.221.158");
        assertThat(result.sshPort()).isEqualTo(10000);
        assertThat(result.sshUsername()).isEqualTo("root");
        assertThat(result.sshPassword()).isEqualTo("password1234");
        assertThat(result.ownerId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("컨테이너 발급 시 SSH 프로비저닝 이후에 DB 저장이 일어난다")
    void provision_callsDockerClientBeforeSavingToDb() {
        User owner = userWithId(1L);
        given(containerPersistenceService.findOwner(1L)).willReturn(owner);
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerPersistenceService.saveContainer(any(Container.class))).willAnswer(inv -> inv.getArgument(0));

        containerService.provision(new ContainerProvisionCommand(1, 512), 1L);

        InOrder inOrder = inOrder(dockerClient, containerPersistenceService);
        inOrder.verify(dockerClient).provisionContainer(1, 512);
        inOrder.verify(containerPersistenceService).saveContainer(any(Container.class));
    }

    @Test
    @DisplayName("존재하지 않는 사용자로 발급을 시도하면 예외가 발생하고 SSH 프로비저닝은 호출되지 않는다")
    void provision_userNotFound_throws() {
        given(containerPersistenceService.findOwner(1L)).willThrow(new UserNotFoundException(1L));

        assertThatThrownBy(() -> containerService.provision(new ContainerProvisionCommand(1, 512), 1L))
                .isInstanceOf(UserNotFoundException.class);

        verify(dockerClient, never()).provisionContainer(anyInt(), anyInt());
    }

    @Test
    @DisplayName("USER는 본인 소유 컨테이너 목록만 조회한다")
    void list_asUser_returnsOwnContainersOnly() {
        User owner = userWithId(1L);
        given(containerRepository.findAllByOwnerId(1L)).willReturn(List.of(containerOwnedBy(100L, owner)));

        List<ContainerSummary> result = containerService.list(1L, Role.USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerId()).isEqualTo(1L);
        verify(containerRepository, never()).findAll();
    }

    @Test
    @DisplayName("ADMIN은 전체 사용자의 컨테이너 목록을 조회한다")
    void list_asAdmin_returnsAllContainers() {
        User owner1 = userWithId(1L);
        User owner2 = userWithId(2L);
        given(containerRepository.findAll()).willReturn(List.of(containerOwnedBy(100L, owner1), containerOwnedBy(200L, owner2)));

        List<ContainerSummary> result = containerService.list(99L, Role.ADMIN);

        assertThat(result).hasSize(2);
        verify(containerRepository, never()).findAllByOwnerId(any());
    }

    @Test
    @DisplayName("본인 소유 컨테이너는 상세 조회할 수 있다")
    void getDetail_ownedByRequester_returnsDetail() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
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

    @Test
    @DisplayName("본인 소유 컨테이너는 삭제할 수 있다")
    void delete_ownedByRequester_deletesContainer() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);

        containerService.delete(100L, 1L, Role.USER);

        verify(dockerClient).removeContainer("docker-100");
        verify(containerPersistenceService).deleteContainer(container);
    }

    @Test
    @DisplayName("컨테이너 삭제 시 SSH 삭제 이후에 DB 삭제가 일어난다")
    void delete_callsDockerClientBeforeDeletingFromDb() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);

        containerService.delete(100L, 1L, Role.USER);

        InOrder inOrder = inOrder(dockerClient, containerPersistenceService);
        inOrder.verify(dockerClient).removeContainer("docker-100");
        inOrder.verify(containerPersistenceService).deleteContainer(container);
    }

    @Test
    @DisplayName("존재하지 않거나 접근 권한이 없는 컨테이너를 삭제하려 하면 예외가 발생하고 SSH 호출은 일어나지 않는다")
    void delete_notAccessible_throwsNotFound() {
        given(containerPersistenceService.getAccessibleContainer(100L, 2L, Role.USER))
                .willThrow(new ContainerNotFoundException(100L));

        assertThatThrownBy(() -> containerService.delete(100L, 2L, Role.USER))
                .isInstanceOf(ContainerNotFoundException.class);
        verify(dockerClient, never()).removeContainer(any());
        verify(containerPersistenceService, never()).deleteContainer(any());
    }

    @Test
    @DisplayName("SSH 삭제는 성공했지만 DB 삭제가 실패하면 예외를 그대로 전파한다")
    void delete_sshSucceedsButDbDeleteFails_propagatesException() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerPersistenceService.getAccessibleContainer(100L, 1L, Role.USER)).willReturn(container);
        RuntimeException dbFailure = new RuntimeException("DB 연결 끊김");
        doThrow(dbFailure).when(containerPersistenceService).deleteContainer(container);

        assertThatThrownBy(() -> containerService.delete(100L, 1L, Role.USER))
                .isSameAs(dbFailure);

        // SSH 컨테이너 삭제 자체는 이미 완료된 상태여야 한다.
        verify(dockerClient).removeContainer("docker-100");
    }

    @Test
    @DisplayName("본인 소유 컨테이너의 메트릭을 조회할 수 있다")
    void getMetrics_ownedByRequester_returnsMetrics() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
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
}
