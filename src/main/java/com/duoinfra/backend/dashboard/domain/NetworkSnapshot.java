package com.duoinfra.backend.dashboard.domain;

import com.duoinfra.backend.container.domain.Container;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "network_snapshots")
public class NetworkSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "container_id", nullable = false)
    private Container container;

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private double networkInMb;

    @Column(nullable = false)
    private double networkOutMb;

    protected NetworkSnapshot() {}

    public NetworkSnapshot(Container container, double networkInMb, double networkOutMb) {
        this.container = container;
        this.networkInMb = networkInMb;
        this.networkOutMb = networkOutMb;
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Container getContainer() { return container; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public double getNetworkInMb() { return networkInMb; }
    public double getNetworkOutMb() { return networkOutMb; }
}
