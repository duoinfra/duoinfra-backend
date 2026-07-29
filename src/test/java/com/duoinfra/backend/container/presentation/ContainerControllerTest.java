package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerDeleteFailedException;
import com.duoinfra.backend.container.application.ContainerMetrics;
import com.duoinfra.backend.container.application.ContainerProvisionFailedException;
import com.duoinfra.backend.container.application.ContainerResult;
import com.duoinfra.backend.container.application.ContainerService;
import com.duoinfra.backend.container.application.ContainerSummary;
import com.duoinfra.backend.container.domain.ContainerFailureReason;
import com.duoinfra.backend.container.domain.ContainerNotFoundException;
import com.duoinfra.backend.container.domain.ContainerNotRunningException;
import com.duoinfra.backend.container.domain.ContainerStatus;
import com.duoinfra.backend.container.domain.InvalidContainerStatusTransitionException;
import com.duoinfra.backend.global.security.AuthenticatedUser;
import com.duoinfra.backend.user.domain.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContainerController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContainerService containerService;

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

    private ContainerResult runningResult(Long id, Long ownerId) {
        return new ContainerResult(id, "abc123", "220.117.221.158", 10000, "root",
                "password1234", 1, 512, ContainerStatus.RUNNING, false, null, LocalDateTime.now(), ownerId);
    }

    @Test
    @DisplayName("POST /api/servers - 서버 발급 성공")
    void provision_success() throws Exception {
        given(containerService.provision(any(), eq(1L))).willReturn(runningResult(10L, 1L));
        authenticateAsUser(1L);

        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(1, 512));

        mockMvc.perform(post("/api/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.containerId").value("abc123"))
                .andExpect(jsonPath("$.host").value("220.117.221.158"))
                .andExpect(jsonPath("$.sshPort").value(10000))
                .andExpect(jsonPath("$.sshUsername").value("root"));
    }

    @Test
    @DisplayName("POST /api/servers - 생성 실패 시 502를 반환한다")
    void provision_failed_returnsBadGateway() throws Exception {
        willThrow(new ContainerProvisionFailedException(10L, ContainerFailureReason.SSH_CONNECTION_FAILED, new RuntimeException()))
                .given(containerService).provision(any(), eq(1L));
        authenticateAsUser(1L);

        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(1, 512));

        mockMvc.perform(post("/api/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("POST /api/servers - cpu 범위 초과 시 400 반환")
    void provision_invalid_cpu() throws Exception {
        authenticateAsUser(1L);
        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(0, 512));

        mockMvc.perform(post("/api/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/servers - memory 범위 미달 시 400 반환")
    void provision_invalid_memory() throws Exception {
        authenticateAsUser(1L);
        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(1, 64));

        mockMvc.perform(post("/api/servers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/servers - USER는 본인 소유 서버 목록을 조회한다")
    void list_asUser() throws Exception {
        ContainerSummary summary = new ContainerSummary(100L, "abc123", "220.117.221.158", 10000, 1, 512,
                ContainerStatus.RUNNING, false, null, LocalDateTime.now(), 1L);
        given(containerService.list(1L, Role.USER)).willReturn(List.of(summary));
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(jsonPath("$[0].ownerId").value(1));
    }

    @Test
    @DisplayName("GET /api/servers/{id} - 본인 소유 서버는 상세 조회할 수 있다")
    void getDetail_success() throws Exception {
        given(containerService.getDetail(100L, 1L, Role.USER)).willReturn(runningResult(100L, 1L));
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/servers/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("GET /api/servers/{id} - USER 응답에는 실패 원인이 노출되지 않고 재시도 가능 여부만 노출된다")
    void getDetail_asUser_hidesFailureReasonButExposesRetryable() throws Exception {
        ContainerResult failedResult = new ContainerResult(100L, null, null, null, null, null, 1, 512,
                ContainerStatus.CREATE_FAILED, true, ContainerFailureReason.SSH_CONNECTION_FAILED, LocalDateTime.now(), 1L);
        given(containerService.getDetail(100L, 1L, Role.USER)).willReturn(failedResult);
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/servers/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/servers/{id} - ADMIN 응답에는 실패 원인이 그대로 노출된다")
    void getDetail_asAdmin_exposesFailureReason() throws Exception {
        ContainerResult failedResult = new ContainerResult(100L, null, null, null, null, null, 1, 512,
                ContainerStatus.CREATE_FAILED, true, ContainerFailureReason.SSH_CONNECTION_FAILED, LocalDateTime.now(), 1L);
        given(containerService.getDetail(100L, 99L, Role.ADMIN)).willReturn(failedResult);
        authenticateAsAdmin(99L);

        mockMvc.perform(get("/api/servers/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.failureReason").value("SSH_CONNECTION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/servers/{id} - 본인 소유가 아닌 서버는 404를 반환한다")
    void getDetail_notOwned_returnsNotFound() throws Exception {
        willThrow(new ContainerNotFoundException(100L))
                .given(containerService).getDetail(100L, 2L, Role.USER);
        authenticateAsUser(2L);

        mockMvc.perform(get("/api/servers/{id}", 100L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/servers/{id} - ADMIN은 다른 사용자 서버도 상세 조회할 수 있다")
    void getDetail_admin_success() throws Exception {
        given(containerService.getDetail(100L, 99L, Role.ADMIN)).willReturn(runningResult(100L, 1L));
        authenticateAsAdmin(99L);

        mockMvc.perform(get("/api/servers/{id}", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(100));
    }

    @Test
    @DisplayName("DELETE /api/servers/{id} - 본인 소유 서버는 삭제할 수 있다")
    void delete_success() throws Exception {
        authenticateAsUser(1L);

        mockMvc.perform(delete("/api/servers/{id}", 100L))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/servers/{id} - 본인 소유가 아닌 서버는 404를 반환한다")
    void delete_notOwned_returnsNotFound() throws Exception {
        willThrow(new ContainerNotFoundException(100L))
                .given(containerService).delete(100L, 2L, Role.USER);
        authenticateAsUser(2L);

        mockMvc.perform(delete("/api/servers/{id}", 100L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/servers/{id} - 삭제 실패 시 502를 반환한다")
    void delete_failed_returnsBadGateway() throws Exception {
        willThrow(new ContainerDeleteFailedException(100L, ContainerFailureReason.DOCKER_COMMAND_FAILED, new RuntimeException()))
                .given(containerService).delete(100L, 1L, Role.USER);
        authenticateAsUser(1L);

        mockMvc.perform(delete("/api/servers/{id}", 100L))
                .andExpect(status().isBadGateway());
    }

    @Test
    @DisplayName("DELETE /api/servers/{id} - 잘못된 상태 전이는 409를 반환한다")
    void delete_invalidTransition_returnsConflict() throws Exception {
        willThrow(new InvalidContainerStatusTransitionException(ContainerStatus.DELETING, ContainerStatus.DELETING))
                .given(containerService).delete(100L, 1L, Role.USER);
        authenticateAsUser(1L);

        mockMvc.perform(delete("/api/servers/{id}", 100L))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /api/servers/{id}/metrics - 본인 소유 서버의 메트릭을 조회할 수 있다")
    void getMetrics_success() throws Exception {
        given(containerService.getMetrics(100L, 1L, Role.USER))
                .willReturn(new ContainerMetrics(10.0, 20.0, 30.0, 40.0, 50.0));
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/servers/{id}/metrics", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cpu").value(10.0))
                .andExpect(jsonPath("$.memory").value(20.0));
    }

    @Test
    @DisplayName("GET /api/servers/{id}/metrics - 본인 소유가 아닌 서버는 404를 반환한다")
    void getMetrics_notOwned_returnsNotFound() throws Exception {
        willThrow(new ContainerNotFoundException(100L))
                .given(containerService).getMetrics(100L, 2L, Role.USER);
        authenticateAsUser(2L);

        mockMvc.perform(get("/api/servers/{id}/metrics", 100L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/servers/{id}/metrics - RUNNING 상태가 아니면 409를 반환한다")
    void getMetrics_notRunning_returnsConflict() throws Exception {
        willThrow(new ContainerNotRunningException(100L, ContainerStatus.CREATING))
                .given(containerService).getMetrics(100L, 1L, Role.USER);
        authenticateAsUser(1L);

        mockMvc.perform(get("/api/servers/{id}/metrics", 100L))
                .andExpect(status().isConflict());
    }
}
