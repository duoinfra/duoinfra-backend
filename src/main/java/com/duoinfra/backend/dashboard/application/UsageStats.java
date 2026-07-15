package com.duoinfra.backend.dashboard.application;

public record UsageStats(
        double cpu,
        double memory,
        double disk
) {
    public static UsageStats zero() {
        return new UsageStats(0, 0, 0);
    }
}
