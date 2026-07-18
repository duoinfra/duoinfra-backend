package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshot;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TrafficCollectorService {

    private static final Logger log = LoggerFactory.getLogger(TrafficCollectorService.class);

    private final ContainerRepository containerRepository;
    private final DockerClient dockerClient;
    private final NetworkSnapshotRepository networkSnapshotRepository;

    public TrafficCollectorService(
            ContainerRepository containerRepository,
            DockerClient dockerClient,
            NetworkSnapshotRepository networkSnapshotRepository) {
        this.containerRepository = containerRepository;
        this.dockerClient = dockerClient;
        this.networkSnapshotRepository = networkSnapshotRepository;
    }

    @Scheduled(fixedDelay = 600_000) // 10분마다
    @Transactional
    public void collect() {
        List<Container> containers = containerRepository.findAll();
        for (Container container : containers) {
            try {
                ContainerMetrics metrics = dockerClient.getContainerMetrics(container.getContainerId());
                networkSnapshotRepository.save(
                        new NetworkSnapshot(container, metrics.networkIn(), metrics.networkOut()));
            } catch (Exception e) {
                log.warn("트래픽 스냅샷 수집 실패 - containerId={}: {}", container.getContainerId(), e.getMessage());
            }
        }
    }
}
