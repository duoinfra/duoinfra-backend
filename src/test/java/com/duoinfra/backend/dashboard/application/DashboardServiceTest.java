package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private DockerClient dockerClient;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(containerRepository, dockerClient);
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
    @DisplayName("USER는 본인 소유 서버 기준으로 usage 평균을 계산한다")
    void getStats_asUser_averagesOwnContainersOnly() {
        User owner = userWithId(1L);
        Container container1 = containerOwnedBy(100L, owner);
        Container container2 = containerOwnedBy(200L, owner);
        given(containerRepository.findAllByOwnerId(1L)).willReturn(List.of(container1, container2));
        given(dockerClient.getContainerMetrics("docker-100")).willReturn(new ContainerMetrics(10, 20, 30, 0, 0));
        given(dockerClient.getContainerMetrics("docker-200")).willReturn(new ContainerMetrics(30, 40, 50, 0, 0));

        DashboardStats result = dashboardService.getStats(1L, Role.USER);

        assertThat(result.serverCount()).isEqualTo(2);
        assertThat(result.usage().cpu()).isCloseTo(20.0, within(0.001));
        assertThat(result.usage().memory()).isCloseTo(30.0, within(0.001));
        assertThat(result.usage().disk()).isCloseTo(40.0, within(0.001));
        verify(containerRepository, never()).findAll();
    }

    @Test
    @DisplayName("ADMIN은 전체 서버 기준으로 usage 평균을 계산한다")
    void getStats_asAdmin_averagesAllContainers() {
        User owner1 = userWithId(1L);
        User owner2 = userWithId(2L);
        Container container1 = containerOwnedBy(100L, owner1);
        Container container2 = containerOwnedBy(200L, owner2);
        given(containerRepository.findAll()).willReturn(List.of(container1, container2));
        given(dockerClient.getContainerMetrics("docker-100")).willReturn(new ContainerMetrics(10, 10, 10, 0, 0));
        given(dockerClient.getContainerMetrics("docker-200")).willReturn(new ContainerMetrics(20, 20, 20, 0, 0));

        DashboardStats result = dashboardService.getStats(99L, Role.ADMIN);

        assertThat(result.serverCount()).isEqualTo(2);
        assertThat(result.usage().cpu()).isCloseTo(15.0, within(0.001));
        verify(containerRepository, never()).findAllByOwnerId(any());
    }

    @Test
    @DisplayName("서버가 없으면 usage는 0으로 반환된다")
    void getStats_noContainers_returnsZeroUsage() {
        given(containerRepository.findAllByOwnerId(1L)).willReturn(Collections.emptyList());

        DashboardStats result = dashboardService.getStats(1L, Role.USER);

        assertThat(result.serverCount()).isZero();
        assertThat(result.usage()).isEqualTo(UsageStats.zero());
        verify(dockerClient, never()).getContainerMetrics(any());
    }

    @Test
    @DisplayName("traffic은 아직 더미 값을 반환한다")
    void getStats_traffic_isDummy() {
        given(containerRepository.findAllByOwnerId(1L)).willReturn(Collections.emptyList());

        DashboardStats result = dashboardService.getStats(1L, Role.USER);

        assertThat(result.traffic()).isEqualTo(TrafficStats.dummy());
    }
}
