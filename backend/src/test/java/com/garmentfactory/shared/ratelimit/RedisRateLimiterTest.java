package com.garmentfactory.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redis;

    @Test
    void allowsRequestsWithinTheLimit() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(List.of(3L, 5000L));

        RateLimitDecision decision = new RedisRateLimiter(redis).hit("key", 5, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.count()).isEqualTo(3);
    }

    @Test
    void rejectsRequestsOverTheLimitAndReportsTheRemainingWindow() {
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(List.of(6L, 12000L));

        RateLimitDecision decision = new RedisRateLimiter(redis).hit("key", 5, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(12));
    }

    @Test
    void failsOpenWhenRedisIsUnavailable() {
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("down"));

        RateLimitDecision decision = new RedisRateLimiter(redis).hit("key", 5, Duration.ofMinutes(1));

        assertThat(decision.allowed()).isTrue();
    }
}