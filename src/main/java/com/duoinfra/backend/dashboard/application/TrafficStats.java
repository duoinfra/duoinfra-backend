package com.duoinfra.backend.dashboard.application;

public record TrafficStats(
        String month,
        double inbound,
        double outbound
) {}
