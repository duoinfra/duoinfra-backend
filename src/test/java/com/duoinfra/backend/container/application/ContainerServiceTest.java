package com.duoinfra.backend.container.application;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ContainerServiceTest {

    @Mock
    private DockerClient dockerClient;

    @Mock
    private ContainerRepository containerRepository;

    private ContainerService containerService;

    @BeforeEach
    void setUp() {
        containerService = new ContainerService(dockerClient, containerRepository, "220.117.221.158");
    }

    @Test
    @DisplayName("컨테이너 발급 성공 시 접속 정보를 반환한다")
    void provision_success() {
        // given
        DockerProvisionResult provisionResult = new DockerProvisionResult("abc123", 10000, "root", "password1234");
        given(dockerClient.provisionContainer(1, 512)).willReturn(provisionResult);
        given(containerRepository.save(any(Container.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ContainerResult result = containerService.provision(new ContainerProvisionCommand(1, 512));

        // then
        assertThat(result.containerId()).isEqualTo("abc123");
        assertThat(result.host()).isEqualTo("220.117.221.158");
        assertThat(result.sshPort()).isEqualTo(10000);
        assertThat(result.sshUsername()).isEqualTo("root");
        assertThat(result.sshPassword()).isEqualTo("password1234");
    }
}
