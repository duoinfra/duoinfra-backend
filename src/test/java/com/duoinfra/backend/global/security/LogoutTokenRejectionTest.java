package com.duoinfra.backend.global.security;

import com.duoinfra.backend.container.application.ContainerService;
import com.duoinfra.backend.container.presentation.ContainerController;
import com.duoinfra.backend.global.config.SecurityConfig;
import com.duoinfra.backend.user.application.JwtTokenProvider;
import com.duoinfra.backend.user.application.TokenBlacklistStore;
import com.duoinfra.backend.user.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// AuthControllerTest 등 대부분의 @WebMvcTest는 addFilters = false로 시큐리티 필터 체인을 꺼두기 때문에,
// "로그아웃(블랙리스트 등록)된 Access Token으로 보호된 API를 호출하면 실제로 401이 되는지"는
// 검증되지 않는다. 이 테스트는 SecurityConfig를 실제로 로드해 JwtAuthenticationFilter +
// authorizeHttpRequests + authenticationEntryPoint로 이어지는 전체 흐름을 검증한다.
@WebMvcTest(ContainerController.class)
@Import(SecurityConfig.class)
class LogoutTokenRejectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ContainerService containerService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private TokenBlacklistStore tokenBlacklistStore;

    @Test
    @DisplayName("로그아웃으로 블랙리스트에 등록된 Access Token으로 보호된 API를 호출하면 401을 반환한다")
    void blacklistedToken_isRejectedWith401() throws Exception {
        given(jwtTokenProvider.validateToken("logged-out-token")).willReturn(true);
        given(tokenBlacklistStore.isBlacklisted("logged-out-token")).willReturn(true);

        mockMvc.perform(get("/api/servers")
                        .header("Authorization", "Bearer logged-out-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("블랙리스트에 없는 유효한 Access Token이면 정상적으로 인증되어 200을 반환한다")
    void validToken_notBlacklisted_isAuthenticated() throws Exception {
        given(jwtTokenProvider.validateToken("valid-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("valid-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("valid-token")).willReturn(Role.USER);
        given(tokenBlacklistStore.isBlacklisted("valid-token")).willReturn(false);
        given(containerService.list(1L, Role.USER)).willReturn(List.of());

        mockMvc.perform(get("/api/servers")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());
    }
}
