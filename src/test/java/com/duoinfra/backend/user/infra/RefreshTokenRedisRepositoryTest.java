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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRedisRepositoryTest {

    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS = 1209600000L; // 14일

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RefreshTokenRedisRepository refreshTokenRedisRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRedisRepository = new RefreshTokenRedisRepository(redisTemplate, REFRESH_TOKEN_EXPIRATION_MILLIS);
    }

    @Test
    @DisplayName("userId를 key로 하여 Refresh Token 만료 기간과 동일한 TTL로 저장한다")
    void save_storesWithUserIdKeyAndMatchingTtl() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        // when
        refreshTokenRedisRepository.save(1L, "refresh-token");

        // then
        then(valueOperations).should().set(
                eq("refresh-token:1"),
                eq("refresh-token"),
                eq(Duration.ofMillis(REFRESH_TOKEN_EXPIRATION_MILLIS))
        );
    }

    @Test
    @DisplayName("저장된 Refresh Token을 userId로 조회할 수 있다")
    void findByUserId_returnsStoredToken() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh-token:1")).willReturn("refresh-token");

        // when
        Optional<String> result = refreshTokenRedisRepository.findByUserId(1L);

        // then
        assertThat(result).contains("refresh-token");
    }

    @Test
    @DisplayName("저장된 Refresh Token이 없으면 빈 값을 반환한다")
    void findByUserId_notFound_returnsEmpty() {
        // given
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("refresh-token:1")).willReturn(null);

        // when
        Optional<String> result = refreshTokenRedisRepository.findByUserId(1L);

        // then
        assertThat(result).isEmpty();
    }
}
