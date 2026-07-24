package com.duoinfra.backend.user.application;

// Access Token 블랙리스트 저장소 포트.
// JWT는 서명/만료만 검증하면 통과되는 stateless 토큰이라, 서버가 "이 토큰은 더 이상 쓸 수 없다"고
// 선언하려면(로그아웃) 그 사실을 별도로 기록해두는 수단이 필요하다. 그 기록 저장소가 이 블랙리스트다.
public interface TokenBlacklistStore {

    // ttlMillis는 반드시 "그 토큰의 남은 만료 시간"으로 넘겨야 한다.
    // 블랙리스트 TTL을 토큰 만료 시간과 정확히 일치시키면, 토큰이 원래 만료될 때 블랙리스트 항목도
    // 자동으로 삭제되어 별도 정리(cleanup) 배치 없이도 메모리가 무한히 쌓이지 않는다.
    void blacklist(String accessToken, long ttlMillis);

    boolean isBlacklisted(String accessToken);
}
