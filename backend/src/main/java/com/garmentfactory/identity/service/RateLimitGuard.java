package com.garmentfactory.identity.service;

import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.exception.TooManyRequestsException;
import com.garmentfactory.shared.ratelimit.RateLimitDecision;
import com.garmentfactory.shared.ratelimit.RateLimiter;
import com.garmentfactory.shared.security.Tokens;
import com.garmentfactory.shared.util.Emails;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The limits of the authentication API in one place. Per-email limits count every attempt whether
 * or not the email exists, so being blocked reveals nothing about which accounts are real.
 */
@Component
public class RateLimitGuard {

    private final RateLimiter limiter;
    private final AppProperties properties;

    public RateLimitGuard(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public void login(String ip, String email) {
        hit("login:ip", ip, 30, Duration.ofMinutes(1));
        hit("login:email", emailKey(email), 10, Duration.ofMinutes(15));
    }

    public void register(String ip) {
        hit("register:ip", ip, 5, Duration.ofHours(1));
    }

    public void passwordRecovery(String ip, String email) {
        hit("forgot:ip", ip, 5, Duration.ofHours(1));
        hit("forgot:email", emailKey(email), 3, Duration.ofHours(1));
    }

    public void resendVerification(String ip, String email) {
        hit("resend:ip", ip, 5, Duration.ofHours(1));
        hit("resend:email", emailKey(email), 3, Duration.ofHours(1));
    }

    public void resetPassword(String ip) {
        hit("reset:ip", ip, 10, Duration.ofHours(1));
    }

    public void refresh(String ip) {
        hit("refresh:ip", ip, 60, Duration.ofMinutes(1));
    }

    public void changePassword(long userId) {
        hit("change-password:user", String.valueOf(userId), 5, Duration.ofMinutes(15));
    }

    private void hit(String rule, String subject, int limit, Duration window) {
        if (!properties.rateLimit().enabled()) {
            return;
        }
        RateLimitDecision decision = limiter.hit("rl:" + rule + ":" + subject, limit, window);
        if (!decision.allowed()) {
            throw new TooManyRequestsException(decision.retryAfter());
        }
    }

    /** Hash prefix instead of the address, so no personal data is stored in Redis. */
    private static String emailKey(String email) {
        return Tokens.sha256Hex(Emails.normalize(email)).substring(0, 32);
    }
}