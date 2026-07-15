package com.duoinfra.backend.dashboard.presentation;

import com.duoinfra.backend.dashboard.application.DashboardService;
import com.duoinfra.backend.dashboard.application.DashboardStats;
import com.duoinfra.backend.dashboard.application.TrafficStats;
import com.duoinfra.backend.dashboard.application.UsageStats;
import com.duoinfra.backend.global.security.AuthenticatedUser;
import com.duoinfra.backend.user.domain.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc(addFilters = false)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, Role.USER), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void authenticateAsAdmin(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId, Role.ADMIN), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @Test
    @DisplayName("GET /api/dashboard/stats - USER는 본인 서버 기준 통계를 조회한다")
    void getStats_asUser() throws Exception {
        DashboardStats stats = new DashboardStats(2, new UsageStats(10.0, 20.0, 30.0), TrafficStats.dummy());
        given(dashboardService.getStats(1L, Role.USER)).willReturn(stats);
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverCount").value(2))
                .andExpect(jsonPath("$.usage.cpu").value(10.0))
                .andExpect(jsonPath("$.usage.memory").value(20.0))
                .andExpect(jsonPath("$.usage.disk").value(30.0))
                .andExpect(jsonPath("$.traffic.inboundTraffic").value(0.0))
                .andExpect(jsonPath("$.traffic.outboundTraffic").value(0.0));
    }

    @Test
    @DisplayName("GET /api/dashboard/stats - ADMIN은 전체 서버 기준 통계를 조회한다")
    void getStats_asAdmin() throws Exception {
        DashboardStats stats = new DashboardStats(5, new UsageStats(15.0, 25.0, 35.0), TrafficStats.dummy());
        given(dashboardService.getStats(99L, Role.ADMIN)).willReturn(stats);
        authenticateAsAdmin(99L);

        mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverCount").value(5))
                .andExpect(jsonPath("$.usage.cpu").value(15.0));
    }
}
