package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.DuplicateEmailException;
import com.duoinfra.backend.user.application.InvalidAccessTokenException;
import com.duoinfra.backend.user.application.InvalidCredentialsException;
import com.duoinfra.backend.user.application.InvalidRefreshTokenException;
import com.duoinfra.backend.user.application.LoginResult;
import com.duoinfra.backend.user.application.LoginService;
import com.duoinfra.backend.user.application.LogoutCommand;
import com.duoinfra.backend.user.application.LogoutService;
import com.duoinfra.backend.user.application.RefreshResult;
import com.duoinfra.backend.user.application.RefreshTokenService;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
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

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private LogoutService logoutService;

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
        given(loginService.login(any())).willReturn(new LoginResult("access-token", "refresh-token", "Bearer", "user@example.com", "duo"));

        String request = objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password1234"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
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

    @Test
    @DisplayName("POST /api/auth/refresh - Refresh Token이 유효하면 새 Access Token을 발급한다")
    void refresh_success() throws Exception {
        given(refreshTokenService.refresh(any())).willReturn(new RefreshResult("new-access-token", "Bearer"));

        String request = objectMapper.writeValueAsString(new RefreshRequest("refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("POST /api/auth/refresh - Refresh Token이 유효하지 않으면 401 반환")
    void refresh_invalidToken() throws Exception {
        given(refreshTokenService.refresh(any())).willThrow(new InvalidRefreshTokenException());

        String request = objectMapper.writeValueAsString(new RefreshRequest("invalid-refresh-token"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/logout - 유효한 Access Token이면 로그아웃 처리 후 204를 반환한다")
    void logout_success() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isNoContent());

        then(logoutService).should().logout(new LogoutCommand("access-token"));
    }

    @Test
    @DisplayName("POST /api/auth/logout - Authorization 헤더가 없으면 401 반환")
    void logout_missingAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());

        then(logoutService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/auth/logout - Authorization 헤더가 Bearer 형식이 아니면 401 반환")
    void logout_malformedAuthorizationHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "access-token"))
                .andExpect(status().isUnauthorized());

        then(logoutService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("POST /api/auth/logout - Access Token이 유효하지 않으면 401 반환")
    void logout_invalidToken() throws Exception {
        willThrow(new InvalidAccessTokenException()).given(logoutService).logout(any());

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}
