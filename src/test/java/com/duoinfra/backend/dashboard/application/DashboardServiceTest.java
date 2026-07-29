package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshot;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshotRepository;
import com.duoinfra.backend.user.domain.Role;
import com.duoinfra.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private ContainerRepository containerRepository;

    @Mock
    private DockerClient dockerClient;

    @Mock
    private NetworkSnapshotRepository networkSnapshotRepository;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(containerRepository, dockerClient, networkSnapshotRepository);
    }

    private User userWithId(Long id) {
        User user = new User(id + "@test.com", "encoded-pw", "user" + id, true);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Container containerOwnedBy(Long containerId, User owner) {
        Container container = Container.createPending(1, 512, owner);
        container.activate("docker-" + containerId, "220.117.221.158", 10000, "root", "password1234");
        ReflectionTestUtils.setField(container, "id", containerId);
        return container;
    }

    private NetworkSnapshot snapshotOf(Container container, double in, double out) {
        NetworkSnapshot snapshot = new NetworkSnapshot(container, in, out);
        ReflectionTestUtils.setField(snapshot, "recordedAt", LocalDateTime.now());
        return snapshot;
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
        given(networkSnapshotRepository.findByContainerIdAndRecordedAtBetween(any(), any(), any()))
                .willReturn(Collections.emptyList());

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
        given(networkSnapshotRepository.findByContainerIdAndRecordedAtBetween(any(), any(), any()))
                .willReturn(Collections.emptyList());

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
    @DisplayName("traffic은 최근 6개월 목록으로 반환된다")
    void getStats_traffic_returns6Months() {
        given(containerRepository.findAllByOwnerId(1L)).willReturn(Collections.emptyList());

        DashboardStats result = dashboardService.getStats(1L, Role.USER);

        assertThat(result.traffic()).hasSize(6);
        assertThat(result.traffic().get(5).month())
                .isEqualTo(YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM")));
    }

    @Test
    @DisplayName("스냅샷이 2개 이상이면 월별 트래픽 차이를 집계한다")
    void getStats_traffic_aggregatesDeltaFromSnapshots() {
        User owner = userWithId(1L);
        Container container = containerOwnedBy(100L, owner);
        given(containerRepository.findAllByOwnerId(1L)).willReturn(List.of(container));
        given(dockerClient.getContainerMetrics("docker-100")).willReturn(new ContainerMetrics(0, 0, 0, 0, 0));

        // 현재 월 스냅샷: 100MB → 300MB (delta = 200MB)
        NetworkSnapshot first = snapshotOf(container, 100.0, 50.0);
        NetworkSnapshot last = snapshotOf(container, 300.0, 150.0);

        YearMonth now = YearMonth.now();
        given(networkSnapshotRepository.findByContainerIdAndRecordedAtBetween(
                eq(100L), any(), any())).willReturn(Collections.emptyList());
        // 현재 월에만 데이터 있도록 - 마지막 월 조회에 스냅샷 주입
        given(networkSnapshotRepository.findByContainerIdAndRecordedAtBetween(
                eq(100L),
                eq(now.atDay(1).atStartOfDay()),
                eq(now.atEndOfMonth().atTime(23, 59, 59))))
                .willReturn(List.of(first, last));

        DashboardStats result = dashboardService.getStats(1L, Role.USER);

        TrafficStats currentMonth = result.traffic().get(5);
        assertThat(currentMonth.inbound()).isCloseTo(200.0, within(0.001));
        assertThat(currentMonth.outbound()).isCloseTo(100.0, within(0.001));
    }
}
