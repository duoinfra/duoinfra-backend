package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.TrafficStats;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrafficResponse(
        @Schema(description = "집계 월 (yyyy-MM)") String month,
        @Schema(description = "인바운드 트래픽 (더미 값, 트래픽 이력 저장 기능 추가 후 실제 데이터로 교체 예정)") double inbound,
        @Schema(description = "아웃바운드 트래픽 (더미 값, 트래픽 이력 저장 기능 추가 후 실제 데이터로 교체 예정)") double outbound
) {
    public static TrafficResponse from(TrafficStats traffic) {
        return new TrafficResponse(traffic.month(), traffic.inbound(), traffic.outbound());
    }
}
