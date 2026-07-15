package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.DashboardStats;
import io.swagger.v3.oas.annotations.media.Schema;

public record DashboardStatsResponse(
        @Schema(description = "조회 대상 서버 수") int serverCount,
        @Schema(description = "평균 리소스 사용률") UsageResponse usage,
        @Schema(description = "트래픽 통계") TrafficResponse traffic
) {
    public static DashboardStatsResponse from(DashboardStats stats) {
        return new DashboardStatsResponse(
                stats.serverCount(),
                UsageResponse.from(stats.usage()),
                TrafficResponse.from(stats.traffic())
        );
    }
}
