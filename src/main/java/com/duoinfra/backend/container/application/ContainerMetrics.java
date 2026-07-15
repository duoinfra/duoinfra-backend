package com.duoinfra.backend.container.application;

public record ContainerMetrics(
        double cpu,
        double memory,
        double networkIn,
        double networkOut
) {}
