package com.duoinfra.backend.user.presentation;

import com.duoinfra.backend.user.application.DuplicateEmailException;
import com.duoinfra.backend.user.application.InvalidAccessTokenException;
import com.duoinfra.backend.user.application.InvalidCredentialsException;
import com.duoinfra.backend.user.application.InvalidRefreshTokenException;
import com.duoinfra.backend.user.application.LoginCommand;
import com.duoinfra.backend.user.application.LoginService;
import com.duoinfra.backend.user.application.LogoutCommand;
import com.duoinfra.backend.user.application.LogoutService;
import com.duoinfra.backend.user.application.RefreshCommand;
import com.duoinfra.backend.user.application.RefreshTokenService;
import com.duoinfra.backend.user.application.SignupCommand;
import com.duoinfra.backend.user.application.SignupService;
import com.duoinfra.backend.user.domain.UserNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "회원가입/로그인 API")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SignupService signupService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final LogoutService logoutService;

    public AuthController(SignupService signupService, LoginService loginService, RefreshTokenService refreshTokenService,
                           LogoutService logoutService) {
        this.signupService = signupService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.logoutService = logoutService;
    }

    @Operation(summary = "회원가입", description = "이메일/비밀번호/닉네임으로 회원가입합니다.")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupCommand command = new SignupCommand(request.email(), request.password(), request.nickname(), request.termsAgreed());
        SignupResponse response = SignupResponse.from(signupService.signup(command));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "로그인", description = "이메일/비밀번호로 로그인하고 Access Token을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginCommand command = new LoginCommand(request.email(), request.password());
        LoginResponse response = LoginResponse.from(loginService.login(command));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Access Token 재발급", description = "Refresh Token을 검증하고, 유효하면 새 Access Token을 발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        RefreshCommand command = new RefreshCommand(request.refreshToken());
        RefreshResponse response = RefreshResponse.from(refreshTokenService.refresh(command));
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "로그아웃", description = "현재 Access Token을 블랙리스트에 등록하고, 저장된 Refresh Token을 삭제합니다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        String accessToken = resolveAccessToken(authorizationHeader);
        logoutService.logout(new LogoutCommand(accessToken));
        return ResponseEntity.noContent().build();
    }

    // /api/auth/** 는 SecurityConfig에서 permitAll()로 열려 있어 JwtAuthenticationFilter가
    // 인증 실패 시에도 요청을 막지 않는다. 따라서 로그아웃 대상 토큰이 있는지는 여기서 직접 검증한다.
    private String resolveAccessToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new InvalidAccessTokenException();
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<String> handleDuplicateEmail(DuplicateEmailException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<String> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(InvalidAccessTokenException.class)
    public ResponseEntity<String> handleInvalidAccessToken(InvalidAccessTokenException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(UserNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
