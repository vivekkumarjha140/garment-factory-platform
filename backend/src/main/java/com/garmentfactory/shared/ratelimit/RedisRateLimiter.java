package com.garmentfactory.shared.ratelimit;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter in Redis. One Lua script does INCR, sets the expiry on the first hit and
 * repairs a key that lost its expiry, so a crash can never leave a counter that never resets.
 * If Redis is unavailable the limiter fails open: the database lockout still protects accounts.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return {count, ttl}
            """, List.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision hit(String key, int limit, Duration window) {
        try {
            List<?> result = redis.execute(SCRIPT, List.of(key), String.valueOf(window.toMillis()));
            if (result == null || result.size() < 2) {
                return RateLimitDecision.allow();
            }
            long count = ((Number) result.get(0)).longValue();
            long ttlMillis = ((Number) result.get(1)).longValue();
            return new RateLimitDecision(count <= limit, count, Duration.ofMillis(Math.max(ttlMillis, 0)));
        } catch (DataAccessException ex) {
            log.warn("Rate limiter unavailable, allowing request: {}", ex.getClass().getSimpleName());
            return RateLimitDecision.allow();
        }
    }
}