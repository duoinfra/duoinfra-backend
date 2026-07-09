package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.Container;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ContainerJpaRepository extends JpaRepository<Container, Long> {
    Optional<Container> findByContainerId(String containerId);
}
