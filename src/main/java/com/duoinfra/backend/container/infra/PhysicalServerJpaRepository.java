package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.PhysicalServer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface PhysicalServerJpaRepository extends JpaRepository<PhysicalServer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PhysicalServer p WHERE p.id = :id")
    Optional<PhysicalServer> findByIdForUpdate(@Param("id") Long id);
}