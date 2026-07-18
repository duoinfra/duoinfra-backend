package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshotRepository;
import com.duoinfra.backend.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrafficCollectorServiceTest {

    @Mock
    private ContainerRepository containerRepository;
    @Mock
    private DockerClient dockerClient;
    @Mock
    private NetworkSnapshotRepository networkSnapshotRepository;

    private TrafficCollectorService trafficCollectorService;

    @BeforeEach
    void setUp() {
        trafficCollectorService = new TrafficCollectorService(containerRepository, dockerClient, networkSnapshotRepository);
    }

    private Container containerWith(Long id, String dockerId) {
        User owner = new User("test@test.com", "pw", "name", true);
        ReflectionTestUtils.setField(owner, "id", 1L);
        Container container = new Container(dockerId, "host", 10000, "root", "pw", 1, 512, owner);
        ReflectionTestUtils.setField(container, "id", id);
        return container;
    }

    @Test
    @DisplayName("실행 중인 컨테이너마다 스냅샷을 저장한다")
    void collect_savesSnapshotForEachContainer() {
        Container c1 = containerWith(1L, "docker-1");
        Container c2 = containerWith(2L, "docker-2");
        given(containerRepository.findAll()).willReturn(List.of(c1, c2));
        given(dockerClient.getContainerMetrics("docker-1")).willReturn(new ContainerMetrics(10, 20, 0, 100.0, 50.0));
        given(dockerClient.getContainerMetrics("docker-2")).willReturn(new ContainerMetrics(5, 10, 0, 200.0, 80.0));

        trafficCollectorService.collect();

        verify(networkSnapshotRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("메트릭 조회 실패 시 해당 컨테이너는 건너뛰고 나머지는 저장한다")
    void collect_skipsFailedContainer() {
        Container c1 = containerWith(1L, "docker-1");
        Container c2 = containerWith(2L, "docker-2");
        given(containerRepository.findAll()).willReturn(List.of(c1, c2));
        given(dockerClient.getContainerMetrics("docker-1")).willThrow(new RuntimeException("연결 실패"));
        given(dockerClient.getContainerMetrics("docker-2")).willReturn(new ContainerMetrics(5, 10, 0, 200.0, 80.0));

        trafficCollectorService.collect();

        verify(networkSnapshotRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("컨테이너가 없으면 스냅샷을 저장하지 않는다")
    void collect_noContainers_savesNothing() {
        given(containerRepository.findAll()).willReturn(List.of());

        trafficCollectorService.collect();

        verify(networkSnapshotRepository, never()).save(any());
    }
}
