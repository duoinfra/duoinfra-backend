package com.duoinfra.backend.dashboard.application;

public record DashboardStats(
        int serverCount,
        UsageStats usage,
        TrafficStats traffic
) {}
