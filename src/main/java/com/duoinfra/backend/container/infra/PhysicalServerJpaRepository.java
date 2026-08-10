package com.duoinfra.backend.container.infra;

import com.duoinfra.backend.container.domain.PhysicalServer;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface PhysicalServerJpaRepository extends JpaRepository<PhysicalServer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "2000")) // 재시도할 실패 지점 명확히 하기 위해 => 타임아웃 설정
    @Query("SELECT p FROM PhysicalServer p WHERE p.id = :id")
    Optional<PhysicalServer> findByIdForUpdate(@Param("id") Long id);
}