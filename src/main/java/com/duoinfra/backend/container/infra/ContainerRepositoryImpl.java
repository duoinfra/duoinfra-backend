package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ContainerRepositoryImpl implements ContainerRepository {

    private final ContainerJpaRepository jpaRepository;

    public ContainerRepositoryImpl(ContainerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Container save(Container container) {
        return jpaRepository.save(container);
    }

    @Override
    public Optional<Container> findByContainerId(String containerId) {
        return jpaRepository.findByContainerId(containerId);
    }
}
