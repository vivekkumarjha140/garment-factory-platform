package com.garmentfactory.shared.ratelimit;

import java.time.Duration;

/** Outcome of counting one request against a limit. */
public record RateLimitDecision(boolean allowed, long count, Duration retryAfter) {

    public static RateLimitDecision allow() {
        return new RateLimitDecision(true, 0, Duration.ZERO);
    }
}