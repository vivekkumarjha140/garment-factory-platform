package com.garmentfactory.shared.exception;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** 429 with the number of seconds after which the caller may retry. */
public class TooManyRequestsException extends ApiException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Too many requests. Please try again later.");
        this.retryAfterSeconds = Math.max(1, retryAfter.toSeconds());
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}