package com.duoinfra.backend.user.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistRedisRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private TokenBlacklistRedisRepository tokenBlacklistRedisRepository;

    @BeforeEach
    void setUp() {
        tokenBlacklistRedisRepository = new TokenBlacklistRedisRepository(redisTemplate);
    }

    @Test
    @DisplayName("토큰을 key로 하여 지정한 TTL로 블랙리스트에 등록한다")
    void blacklist_storesWithTokenKeyAndGivenTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        tokenBlacklistRedisRepository.blacklist("access-token", 60_000L);

        // then
        then(valueOperations).should().set(
                eq("blacklist:access-token"),
                eq(Boolean.TRUE),
                eq(Duration.ofMillis(60_000L))
        );
    }

    @Test
    @DisplayName("남은 만료 시간이 0 이하이면 저장하지 않는다")
    void blacklist_nonPositiveTtl_doesNothing() {
        // when
        tokenBlacklistRedisRepository.blacklist("access-token", 0L);

        // then
        then(redisTemplate).should(never()).opsForValue();
    }

    @Test
    @DisplayName("블랙리스트에 등록된 토큰은 true를 반환한다")
    void isBlacklisted_returnsTrue_whenPresent() {
        // given
        given(redisTemplate.hasKey("blacklist:access-token")).willReturn(true);

        // when & then
        assertThat(tokenBlacklistRedisRepository.isBlacklisted("access-token")).isTrue();
    }

    @Test
    @DisplayName("블랙리스트에 없는 토큰은 false를 반환한다")
    void isBlacklisted_returnsFalse_whenAbsent() {
        // given
        given(redisTemplate.hasKey("blacklist:access-token")).willReturn(false);

        // when & then
        assertThat(tokenBlacklistRedisRepository.isBlacklisted("access-token")).isFalse();
    }
}
