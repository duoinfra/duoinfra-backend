package com.duoinfra.backend.global.security;

import com.duoinfra.backend.user.application.JwtTokenProvider;
import com.duoinfra.backend.user.application.TokenBlacklistStore;
import com.duoinfra.backend.user.domain.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistStore tokenBlacklistStore;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, TokenBlacklistStore tokenBlacklistStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistStore = tokenBlacklistStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        // 서명/만료 검증(validateToken)을 통과했더라도, 로그아웃으로 블랙리스트에 등록된 토큰이면
        // 인증 정보를 설정하지 않는다. 그러면 이후 authorizeHttpRequests 단계에서 미인증으로 처리되어
        // 보호된 API는 401을 반환하게 된다.
        if (token != null && jwtTokenProvider.validateToken(token) && !tokenBlacklistStore.isBlacklisted(token)) {
            Long userId = jwtTokenProvider.getUserId(token);
            Role role = jwtTokenProvider.getRole(token);
            AuthenticatedUser principal = new AuthenticatedUser(userId, role);
            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
