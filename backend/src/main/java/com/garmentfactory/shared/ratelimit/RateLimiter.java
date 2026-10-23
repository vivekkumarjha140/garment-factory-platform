package com.garmentfactory.shared.ratelimit;

import java.time.Duration;

public interface RateLimiter {

    /** Counts one hit for {@code key} in a fixed window and reports whether it is still within {@code limit}. */
    RateLimitDecision hit(String key, int limit, Duration window);
}