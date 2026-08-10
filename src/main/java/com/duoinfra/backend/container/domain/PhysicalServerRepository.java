package com.duoinfra.backend.container.domain;

import java.util.Optional;

public interface PhysicalServerRepository {
    PhysicalServer save(PhysicalServer physicalServer);
    Optional<PhysicalServer> findById(Long id);
    Optional<PhysicalServer> findByIdForUpdate(Long id);
}
