package com.duoinfra.backend.user.application;

import java.util.Optional;

// Refresh Token 저장소 포트. JWT는 한번 발급되면 서버가 임의로 무효화할 수 없기 때문에,
// "현재 유효한 Refresh Token이 무엇인지"를 서버(Redis)에도 별도로 들고 있어야
// - 로그인 시 이전 Refresh Token을 자동으로 교체(사실상 무효화)하고
// - 이후 로그아웃/탈취 대응 기능이 추가될 때 서버 측에서 즉시 무효화할 지점을 가질 수 있다.
public interface RefreshTokenStore {

    void save(Long userId, String refreshToken);

    Optional<String> findByUserId(Long userId);

    // 로그아웃 시 호출한다. Access Token을 블랙리스트에 등록하는 것과 별개로,
    // Refresh Token까지 지워야 로그아웃 이후 그 계정이 재발급(refresh)을 통해
    // 새 Access Token을 계속 얻어가는 것을 막을 수 있다.
    void deleteByUserId(Long userId);
}
