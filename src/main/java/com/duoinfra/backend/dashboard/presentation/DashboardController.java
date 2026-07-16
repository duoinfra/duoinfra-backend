package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.DashboardService;
import com.duoinfra.backend.dashboard.application.DashboardStats;
import com.duoinfra.backend.global.config.OpenApiConfig;
import com.duoinfra.backend.global.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Dashboard", description = "대시보드 통계 API")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "대시보드 통계 조회",
            description = "USER는 본인 서버 기준, ADMIN은 전체 서버 기준 통계를 조회합니다. " +
                    "usage(cpu, memory, disk)는 서버들의 실제 메트릭 평균값이며, traffic은 트래픽 이력 저장 기능이 아직 없어 더미 값입니다.")
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(@AuthenticationPrincipal AuthenticatedUser principal) {
        DashboardStats stats = dashboardService.getStats(principal.userId(), principal.role());
        return ResponseEntity.ok(DashboardStatsResponse.from(stats));
    }
}
