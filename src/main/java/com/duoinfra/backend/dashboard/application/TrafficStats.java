package com.duoinfra.backend.dashboard.application;

public record TrafficStats(
        double inboundTraffic,
        double outboundTraffic
) {
    // TODO: 트래픽 이력 저장 기능이 추가되면 더미 값 대신 실제 집계 데이터로 교체할 것.
    public static TrafficStats dummy() {
        return new TrafficStats(0, 0);
    }
}
