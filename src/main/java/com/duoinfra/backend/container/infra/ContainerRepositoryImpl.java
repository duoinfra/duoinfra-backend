package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Override
    public Optional<Container> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Container> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public List<Container> findAllByOwnerId(Long ownerId) {
        return jpaRepository.findAllByOwner_Id(ownerId);
    }

    @Override
    public void delete(Container container) {
        jpaRepository.delete(container);
    }
}
