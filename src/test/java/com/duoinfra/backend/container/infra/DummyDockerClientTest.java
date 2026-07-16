package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerProvisionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DummyDockerClientTest {

    private final DummyDockerClient dummyDockerClient = new DummyDockerClient(new PortAllocator());

    @Test
    @DisplayName("컨테이너 발급 시 접속 정보를 즉시 반환한다")
    void provisionContainer_returnsFakeConnectionInfo() {
        DockerProvisionResult result = dummyDockerClient.provisionContainer(1, 512);

        assertThat(result.containerId()).isNotBlank();
        assertThat(result.sshUsername()).isEqualTo("root");
        assertThat(result.sshPassword()).hasSize(16);
        assertThat(result.sshPort()).isGreaterThanOrEqualTo(10000);
    }

    @Test
    @DisplayName("컨테이너 삭제는 예외 없이 처리된다")
    void removeContainer_doesNotThrow() {
        assertThatCode(() -> dummyDockerClient.removeContainer("dummy-abc")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("메트릭 조회 시 가짜 값을 반환한다")
    void getContainerMetrics_returnsFakeMetrics() {
        ContainerMetrics metrics = dummyDockerClient.getContainerMetrics("dummy-abc");

        assertThat(metrics.cpu()).isBetween(0.0, 100.0);
        assertThat(metrics.memory()).isBetween(0.0, 100.0);
        assertThat(metrics.disk()).isBetween(0.0, 100.0);
        assertThat(metrics.networkIn()).isBetween(0.0, 1024.0);
        assertThat(metrics.networkOut()).isBetween(0.0, 1024.0);
    }
}
