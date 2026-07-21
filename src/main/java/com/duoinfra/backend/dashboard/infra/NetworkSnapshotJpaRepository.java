package com.duoinfra.backend.dashboard.infra;

import com.duoinfra.backend.dashboard.domain.NetworkSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkSnapshotJpaRepository extends JpaRepository<NetworkSnapshot, Long> {
    List<NetworkSnapshot> findByContainer_IdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long containerId, LocalDateTime from, LocalDateTime to);
}
