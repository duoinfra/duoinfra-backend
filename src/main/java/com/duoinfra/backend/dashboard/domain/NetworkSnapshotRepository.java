package com.duoinfra.backend.dashboard.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface NetworkSnapshotRepository {
    void save(NetworkSnapshot snapshot);
    List<NetworkSnapshot> findByContainerIdAndRecordedAtBetween(Long containerId, LocalDateTime from, LocalDateTime to);
}
