package com.duoinfra.backend.global.config;

import com.duoinfra.backend.global.security.JwtAuthenticationFilter;
import com.duoinfra.backend.user.application.JwtTokenProvider;
import com.duoinfra.backend.user.application.TokenBlacklistStore;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] PERMIT_ALL_PATHS = {
            "/api/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            // Prometheus가 인증 없이 스크랩할 수 있어야 하는 단일 엔드포인트.
            // /actuator/** 전체를 열지 않고 이 경로만 명시적으로 허용한다.
            "/actuator/prometheus",
            // 컨트롤러에서 처리되지 않은 예외는 서블릿 컨테이너가 /error로 내부 forward한다.
            // Spring Boot는 이 forward도 기본적으로 시큐리티 필터 체인에 태우는데(ERROR DispatcherType 포함),
            // 원 요청에서 이미 SecurityContext가 정리된 뒤라 인증되지 않은 요청으로 보여 401로 가로채 버린다.
            // 그러면 원래 500이었어야 할 에러가 클라이언트에는 "인증이 필요합니다"(401)로 잘못 보인다.
            // /error를 permitAll로 열어야 BasicErrorController가 본래 상태 코드(500 등)로 응답할 수 있다.
            "/error",
    };

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistStore tokenBlacklistStore;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider, TokenBlacklistStore tokenBlacklistStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistStore = tokenBlacklistStore;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("text/plain;charset=UTF-8");
                            response.getWriter().write("인증이 필요합니다.");
                        })
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistStore), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
