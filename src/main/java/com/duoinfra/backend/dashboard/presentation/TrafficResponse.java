package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.TrafficStats;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrafficResponse(
        @Schema(description = "인바운드 트래픽 (더미 값, 트래픽 이력 저장 기능 추가 후 실제 데이터로 교체 예정)") double inboundTraffic,
        @Schema(description = "아웃바운드 트래픽 (더미 값, 트래픽 이력 저장 기능 추가 후 실제 데이터로 교체 예정)") double outboundTraffic
) {
    public static TrafficResponse from(TrafficStats traffic) {
        return new TrafficResponse(traffic.inboundTraffic(), traffic.outboundTraffic());
    }
}
