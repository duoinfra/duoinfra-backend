package com.duoinfra.backend.dashboard.application;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

public record TrafficStats(
        String month,
        double inbound,
        double outbound
) {
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    // TODO: 트래픽 이력 저장 기능이 추가되면 더미 값 대신 실제 집계 데이터로 교체할 것.
    public static TrafficStats dummyForCurrentMonth() {
        return new TrafficStats(YearMonth.now().format(MONTH_FORMAT), 0, 0);
    }
}
