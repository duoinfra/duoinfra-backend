package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.PhysicalServer;
import com.duoinfra.backend.container.domain.PhysicalServerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PhysicalServerRepositoryImpl implements PhysicalServerRepository {

    private final PhysicalServerJpaRepository physicalServerJpaRepository;

    public PhysicalServerRepositoryImpl(PhysicalServerJpaRepository physicalServerJpaRepository) {
        this.physicalServerJpaRepository = physicalServerJpaRepository;
    }

    @Override
    public PhysicalServer save(PhysicalServer physicalServer) {
        return physicalServerJpaRepository.save(physicalServer);
    }

    @Override
    public Optional<PhysicalServer> findById(Long id) {
        return physicalServerJpaRepository.findById(id);
    }

    @Override
    public Optional<PhysicalServer> findByIdForUpdate(Long id) {
        return physicalServerJpaRepository.findByIdForUpdate(id);
    }
}