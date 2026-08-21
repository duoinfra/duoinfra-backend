package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

interface ContainerJpaRepository extends JpaRepository<Container, Long> {
    Optional<Container> findByContainerId(String containerId);
    List<Container> findAllByOwner_Id(Long ownerId);
    List<Container> findAllByStatusNot(ContainerStatus excludedStatus);
    List<Container> findAllByOwner_IdAndStatusNot(Long ownerId, ContainerStatus excludedStatus);
    List<Container> findAllByStatusAndCreatedAtBefore(ContainerStatus status, LocalDateTime threshold);
}
