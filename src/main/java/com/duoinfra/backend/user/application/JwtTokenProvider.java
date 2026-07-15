package com.duoinfra.backend.user.application;

import com.duoinfra.backend.user.domain.Role;

public interface JwtTokenProvider {
    String generateAccessToken(Long userId, String email, Role role);
    boolean validateToken(String token);
    Long getUserId(String token);
    Role getRole(String token);
}
