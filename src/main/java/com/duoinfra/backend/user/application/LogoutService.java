package com.duoinfra.backend.user.application;

import org.springframework.stereotype.Service;

@Service
public class LogoutService {

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistStore tokenBlacklistStore;
    private final RefreshTokenStore refreshTokenStore;

    public LogoutService(JwtTokenProvider jwtTokenProvider, TokenBlacklistStore tokenBlacklistStore,
                          RefreshTokenStore refreshTokenStore) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenBlacklistStore = tokenBlacklistStore;
        this.refreshTokenStore = refreshTokenStore;
    }

    public void logout(LogoutCommand command) {
        String accessToken = command.accessToken();

        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new InvalidAccessTokenException();
        }

        Long userId = jwtTokenProvider.getUserId(accessToken);

        // 블랙리스트 TTL을 "이 토큰이 원래 만료되기까지 남은 시간"으로 정확히 맞춘다.
        // - TTL을 더 길게(혹은 무기한으로) 잡으면, 어차피 서명 검증만으로도 통과 못 하는 만료된 토큰의
        //   기록이 Redis에 불필요하게 계속 남아 메모리를 낭비한다.
        // - TTL을 더 짧게 잡으면, 토큰이 실제로는 아직 유효 기간 안인데 블랙리스트 항목만 먼저 삭제되어
        //   로그아웃했던 토큰이 다시 통과하는 보안 허점이 생긴다.
        // 즉 "남은 만료 시간과 정확히 일치"가 유일하게 안전하면서도 낭비가 없는 값이다.
        long ttlMillis = jwtTokenProvider.getExpiration(accessToken).getTime() - System.currentTimeMillis();
        tokenBlacklistStore.blacklist(accessToken, ttlMillis);

        // Access Token 블랙리스트 등록만으로는 부족하다. Refresh Token이 살아있으면
        // 로그아웃 직후 /api/auth/refresh를 호출해 새 Access Token을 재발급받아 로그아웃을 무력화할 수 있으므로
        // 함께 삭제해 재발급 경로도 막는다.
        refreshTokenStore.deleteByUserId(userId);
    }
}
