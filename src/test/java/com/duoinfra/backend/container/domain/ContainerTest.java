package com.duoinfra.backend.container.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerTest {

    @Test
    @DisplayName("컨테이너 생성 시 상태는 RUNNING이다")
    void create_status_running() {
        Container container = new Container("abc123", "192.168.0.1", 10000, "root", "pass", 1, 512);
        assertThat(container.getStatus()).isEqualTo(ContainerStatus.RUNNING);
    }

    @Test
    @DisplayName("컨테이너 생성 시 전달한 값이 저장된다")
    void create_values() {
        Container container = new Container("abc123", "192.168.0.1", 10000, "root", "pass", 2, 1024);
        assertThat(container.getContainerId()).isEqualTo("abc123");
        assertThat(container.getHost()).isEqualTo("192.168.0.1");
        assertThat(container.getSshPort()).isEqualTo(10000);
        assertThat(container.getSshUsername()).isEqualTo("root");
        assertThat(container.getSshPassword()).isEqualTo("pass");
        assertThat(container.getCpu()).isEqualTo(2);
        assertThat(container.getMemory()).isEqualTo(1024);
    }
}
