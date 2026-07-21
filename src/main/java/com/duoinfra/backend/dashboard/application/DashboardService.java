package com.duoinfra.backend.dashboard.application;

import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.DockerClient;
import com.duoinfra.backend.container.domain.Container;
import com.duoinfra.backend.container.domain.ContainerRepository;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshot;
import com.duoinfra.backend.dashboard.domain.NetworkSnapshotRepository;
import com.duoinfra.backend.user.domain.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardService {

    private static final int TRAFFIC_MONTHS = 6;
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ContainerRepository containerRepository;
    private final DockerClient dockerClient;
    private final NetworkSnapshotRepository networkSnapshotRepository;

    public DashboardService(ContainerRepository containerRepository, DockerClient dockerClient,
                            NetworkSnapshotRepository networkSnapshotRepository) {
        this.containerRepository = containerRepository;
        this.dockerClient = dockerClient;
        this.networkSnapshotRepository = networkSnapshotRepository;
    }

    @Transactional(readOnly = true)
    public DashboardStats getStats(Long requesterId, Role requesterRole) {
        List<Container> containers = requesterRole == Role.ADMIN
                ? containerRepository.findAll()
                : containerRepository.findAllByOwnerId(requesterId);

        UsageStats usage = calculateUsage(containers);
        List<TrafficStats> traffic = calculateTraffic(containers);

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

    private List<TrafficStats> calculateTraffic(List<Container> containers) {
        List<TrafficStats> result = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = TRAFFIC_MONTHS - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            LocalDateTime from = month.atDay(1).atStartOfDay();
            LocalDateTime to = month.atEndOfMonth().atTime(23, 59, 59);

            double totalIn = 0;
            double totalOut = 0;

            for (Container container : containers) {
                List<NetworkSnapshot> snapshots = networkSnapshotRepository
                        .findByContainerIdAndRecordedAtBetween(container.getId(), from, to);

                if (snapshots.size() >= 2) {
                    NetworkSnapshot first = snapshots.get(0);
                    NetworkSnapshot last = snapshots.get(snapshots.size() - 1);
                    totalIn += Math.max(0, last.getNetworkInMb() - first.getNetworkInMb());
                    totalOut += Math.max(0, last.getNetworkOutMb() - first.getNetworkOutMb());
                }
            }

            result.add(new TrafficStats(month.format(MONTH_FORMAT), totalIn, totalOut));
        }

        return result;
    }
}
