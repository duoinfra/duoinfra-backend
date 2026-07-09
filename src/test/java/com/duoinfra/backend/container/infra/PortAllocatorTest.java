package com.duoinfra.backend.container.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortAllocatorTest {

    @Test
    @DisplayName("포트를 순차적으로 발급한다")
    void allocate_sequential() {
        PortAllocator allocator = new PortAllocator();
        int first = allocator.allocate();
        int second = allocator.allocate();
        assertThat(second).isEqualTo(first + 1);
    }

    @Test
    @DisplayName("포트 범위 내에서 발급한다")
    void allocate_within_range() {
        PortAllocator allocator = new PortAllocator();
        int port = allocator.allocate();
        assertThat(port).isBetween(10000, 20000);
    }
}
