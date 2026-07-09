package com.duoinfra.backend.container.domain;

import java.util.Optional;

public interface ContainerRepository {
    Container save(Container container);
    Optional<Container> findByContainerId(String containerId);
}
