package com.duoinfra.backend.container.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ContainerRepository {
    Container save(Container container);
    Optional<Container> findByContainerId(String containerId);
    Optional<Container> findById(Long id);
    List<Container> findAll();
    List<Container> findAllByOwnerId(Long ownerId);
    List<Container> findAllExcludingStatus(ContainerStatus excludedStatus);
    List<Container> findAllByOwnerIdExcludingStatus(Long ownerId, ContainerStatus excludedStatus);
    List<Container> findAllByStatusAndCreatedAtBefore(ContainerStatus status, LocalDateTime threshold);
}
