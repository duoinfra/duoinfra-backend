package com.duoinfra.backend.container.presentation;

import com.duoinfra.backend.container.application.ContainerResult;
import com.duoinfra.backend.container.application.ContainerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContainerController.class)
class ContainerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ContainerService containerService;

    @Test
    @DisplayName("POST /api/containers - 컨테이너 발급 성공")
    void provision_success() throws Exception {
        ContainerResult result = new ContainerResult("abc123", "220.117.221.158", 10000, "root", "password1234");
        given(containerService.provision(any())).willReturn(result);

        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(1, 512));

        mockMvc.perform(post("/api/containers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerId").value("abc123"))
                .andExpect(jsonPath("$.host").value("220.117.221.158"))
                .andExpect(jsonPath("$.sshPort").value(10000))
                .andExpect(jsonPath("$.sshUsername").value("root"));
    }

    @Test
    @DisplayName("POST /api/containers - cpu 범위 초과 시 400 반환")
    void provision_invalid_cpu() throws Exception {
        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(0, 512));

        mockMvc.perform(post("/api/containers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/containers - memory 범위 미달 시 400 반환")
    void provision_invalid_memory() throws Exception {
        String request = objectMapper.writeValueAsString(new ContainerProvisionRequest(1, 64));

        mockMvc.perform(post("/api/containers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }
}
