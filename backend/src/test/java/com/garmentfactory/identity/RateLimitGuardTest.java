package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.garmentfactory.identity.service.RateLimitGuard;
import com.garmentfactory.shared.exception.TooManyRequestsException;
import com.garmentfactory.shared.ratelimit.RateLimitDecision;
import com.garmentfactory.shared.ratelimit.RateLimiter;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitGuardTest {

    @Mock
    private RateLimiter limiter;

    private RateLimitGuard guard(boolean enabled) {
        return new RateLimitGuard(limiter, TestProperties.appProperties(enabled));
    }

    @Test
    void requestWithinTheLimitPasses() {
        when(limiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(new RateLimitDecision(true, 1, Duration.ZERO));

        assertThatCode(() -> guard(true).register("203.0.113.9")).doesNotThrowAnyException();
    }

    @Test
    void requestOverTheLimitIsRejectedWithRetryAfter() {
        when(limiter.hit(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(new RateLimitDecision(false, 31, Duration.ofSeconds(42)));

        assertThatThrownBy(() -> guard(true).register("203.0.113.9"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(42));
    }

    @Test
    void disabledGuardNeverCallsTheLimiter() {
        guard(false).login("203.0.113.9", "user@example.com");

        verify(limiter, never()).hit(anyString(), anyInt(), any(Duration.class));
    }

    @Test
    void loginCountsBothTheIpAndTheEmail() {
        when(limiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(new RateLimitDecision(true, 1, Duration.ZERO));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);

        guard(true).login("203.0.113.9", "user@example.com");

        verify(limiter, times(2)).hit(keys.capture(), anyInt(), any(Duration.class));
        List<String> values = keys.getAllValues();
        assertThat(values.get(0)).isEqualTo("rl:login:ip:203.0.113.9");
        assertThat(values.get(1)).startsWith("rl:login:email:");
    }

    @Test
    void emailKeysNeverContainTheAddressAndIgnoreCase() {
        when(limiter.hit(anyString(), anyInt(), any(Duration.class))).thenReturn(new RateLimitDecision(true, 1, Duration.ZERO));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);

        guard(true).passwordRecovery("203.0.113.9", "User@Example.com");
        guard(true).passwordRecovery("203.0.113.9", " user@example.com ");

        verify(limiter, times(4)).hit(keys.capture(), anyInt(), any(Duration.class));
        List<String> emailKeys = keys.getAllValues().stream().filter(k -> k.startsWith("rl:forgot:email:")).toList();
        assertThat(emailKeys).hasSize(2);
        assertThat(emailKeys.get(0)).isEqualTo(emailKeys.get(1)).doesNotContain("@").doesNotContain("example");
    }
}