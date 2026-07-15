package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.DuplicateEmailException;
import com.duoinfra.backend.user.application.InvalidCredentialsException;
import com.duoinfra.backend.user.application.LoginResult;
import com.duoinfra.backend.user.application.LoginService;
import com.duoinfra.backend.user.application.SignupResult;
import com.duoinfra.backend.user.application.SignupService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SignupService signupService;

    @MockitoBean
    private LoginService loginService;

    @Test
    @DisplayName("POST /api/auth/signup - 회원가입 성공")
    void signup_success() throws Exception {
        given(signupService.signup(any())).willReturn(new SignupResult(1L, "user@example.com", "duo"));

        String request = objectMapper.writeValueAsString(new SignupRequest("user@example.com", "password1234", "duo", true));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.nickname").value("duo"));
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이용약관 미동의 시 400 반환")
    void signup_termsNotAgreed() throws Exception {
        String request = objectMapper.writeValueAsString(new SignupRequest("user@example.com", "password1234", "duo", false));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/signup - 이메일 중복 시 409 반환")
    void signup_duplicateEmail() throws Exception {
        given(signupService.signup(any())).willThrow(new DuplicateEmailException("user@example.com"));

        String request = objectMapper.writeValueAsString(new SignupRequest("user@example.com", "password1234", "duo", true));

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /api/auth/login - 로그인 성공")
    void login_success() throws Exception {
        given(loginService.login(any())).willReturn(new LoginResult("access-token", "Bearer", "user@example.com", "duo"));

        String request = objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password1234"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/login - 인증 실패 시 401 반환")
    void login_invalidCredentials() throws Exception {
        given(loginService.login(any())).willThrow(new InvalidCredentialsException());

        String request = objectMapper.writeValueAsString(new LoginRequest("user@example.com", "wrongPassword"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }
}
