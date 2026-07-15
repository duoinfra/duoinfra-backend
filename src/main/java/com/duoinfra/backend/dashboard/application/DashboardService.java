package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DashboardService {

    private final ContainerRepository containerRepository;
    private final DockerClient dockerClient;

    public DashboardService(ContainerRepository containerRepository, DockerClient dockerClient) {
        this.containerRepository = containerRepository;
        this.dockerClient = dockerClient;
    }

    @Transactional(readOnly = true)
    public DashboardStats getStats(Long requesterId, Role requesterRole) {
        List<Container> containers = requesterRole == Role.ADMIN
                ? containerRepository.findAll()
                : containerRepository.findAllByOwnerId(requesterId);

        UsageStats usage = calculateUsage(containers);
        TrafficStats traffic = TrafficStats.dummy();

        return new DashboardStats(containers.size(), usage, traffic);
    }

    private UsageStats calculateUsage(List<Container> containers) {
        if (containers.isEmpty()) {
            return UsageStats.zero();
        }

        List<ContainerMetrics> metricsList = containers.stream()
                .map(container -> dockerClient.getContainerMetrics(container.getContainerId()))
                .toList();

        double avgCpu = metricsList.stream().mapToDouble(ContainerMetrics::cpu).average().orElse(0);
        double avgMemory = metricsList.stream().mapToDouble(ContainerMetrics::memory).average().orElse(0);
        double avgDisk = metricsList.stream().mapToDouble(ContainerMetrics::disk).average().orElse(0);

        return new UsageStats(avgCpu, avgMemory, avgDisk);
    }
}
