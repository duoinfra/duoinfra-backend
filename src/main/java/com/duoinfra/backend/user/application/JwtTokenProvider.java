package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.Role;

public interface JwtTokenProvider {
    String generateAccessToken(Long userId, String email, Role role);

    // Refresh Token은 재발급 용도로만 쓰이므로 email/role 없이 userId만 담아 발급한다.
    // (재발급 시점의 최신 email/role은 UserRepository에서 다시 조회해서 사용한다)
    String generateRefreshToken(Long userId);

    boolean validateToken(String token);
    Long getUserId(String token);
    Role getRole(String token);
}
