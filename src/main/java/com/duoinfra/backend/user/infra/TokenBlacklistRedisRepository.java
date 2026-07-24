package com.duoinfra.backend.user.infra;

import com.duoinfra.backend.user.application.TokenBlacklistStore;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
public class TokenBlacklistRedisRepository implements TokenBlacklistStore {

    // key를 토큰 문자열 자체로 하여 다른 종류의 값과 충돌하지 않도록 prefix로 네임스페이스를 분리한다.
    private static final String KEY_PREFIX = "blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;

    public TokenBlacklistRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void blacklist(String accessToken, long ttlMillis) {
        if (ttlMillis <= 0) {
            // 이미 만료된 토큰은 애초에 인증에 통과할 수 없으므로 블랙리스트에 넣을 필요가 없다.
            return;
        }

        // TTL을 호출자가 넘긴 "토큰의 남은 만료 시간"과 정확히 일치시킨다.
        // 이렇게 해야 토큰이 원래 만료되는 시점에 블랙리스트 항목도 Redis에서 자동 삭제되어,
        // 이미 무효한(만료된) 토큰의 블랙리스트 기록이 메모리에 계속 남아있는 낭비가 없다.
        redisTemplate.opsForValue().set(
                key(accessToken),
                Boolean.TRUE,
                Duration.ofMillis(ttlMillis)
        );
    }

    @Override
    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(accessToken)));
    }

    private String key(String accessToken) {
        return KEY_PREFIX + accessToken;
    }
}
