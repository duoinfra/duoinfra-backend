package com.duoinfra.backend.dashboard.infra;

import com.duoinfra.backend.dashboard.domain.NetworkSnapshot;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshotRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class NetworkSnapshotRepositoryImpl implements NetworkSnapshotRepository {

    private final NetworkSnapshotJpaRepository jpaRepository;

    public NetworkSnapshotRepositoryImpl(NetworkSnapshotJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(NetworkSnapshot snapshot) {
        jpaRepository.save(snapshot);
    }

    @Override
    public List<NetworkSnapshot> findByContainerIdAndRecordedAtBetween(
            Long containerId, LocalDateTime from, LocalDateTime to) {
        return jpaRepository.findByContainer_IdAndRecordedAtBetweenOrderByRecordedAtAsc(containerId, from, to);
    }
}
