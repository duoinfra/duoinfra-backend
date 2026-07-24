package com.duoinfra.backend.user.infra;

import com.duoinfra.backend.user.application.RefreshTokenStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
public class RefreshTokenRedisRepository implements RefreshTokenStore {

    // key를 userId 하나로만 쓰면 다른 종류의 값과 충돌할 수 있어 prefix로 네임스페이스를 분리한다.
    private static final String KEY_PREFIX = "refresh-token:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long refreshTokenExpirationMillis;

    public RefreshTokenRedisRepository(RedisTemplate<String, Object> redisTemplate,
                                        @Value("${jwt.refresh-token-expiration}") long refreshTokenExpirationMillis) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenExpirationMillis = refreshTokenExpirationMillis;
    }

    @Override
    public void save(Long userId, String refreshToken) {
        // TTL을 Refresh Token의 JWT 만료 기간과 동일하게 맞춘다.
        // 이렇게 해야 "Redis에는 남아있는데 JWT는 이미 만료됨" 또는 그 반대의 불일치가 생기지 않고,
        // 만료된 Refresh Token은 Redis에서도 자동으로 사라져 별도 정리(cleanup) 로직이 필요 없다.
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenExpirationMillis)
        );
    }

    @Override
    public Optional<String> findByUserId(Long userId) {
        Object value = redisTemplate.opsForValue().get(key(userId));
        return Optional.ofNullable(value).map(Object::toString);
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
