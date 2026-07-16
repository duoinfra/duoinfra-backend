package com.duoinfra.backend.dashboard.application;

import java.util.List;

public record DashboardStats(
        int serverCount,
        UsageStats usage,
        List<TrafficStats> traffic
) {}
