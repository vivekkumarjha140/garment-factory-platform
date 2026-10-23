package com.garmentfactory.identity;

import com.garmentfactory.shared.config.AppProperties;
import java.time.Duration;
import java.util.List;

/** Fixed settings for plain unit tests that do not start Spring. */
final class TestProperties {

    private TestProperties() {
    }

    static AppProperties appProperties() {
        return appProperties(false);
    }

    static AppProperties appProperties(boolean rateLimitEnabled) {
        return new AppProperties(
                "http://localhost:5173",
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Mail("no-reply@test.local"),
                new AppProperties.Security(
                        new AppProperties.Jwt("unused", "test-issuer", Duration.ofMinutes(15)),
                        new AppProperties.RefreshTokenProperties(Duration.ofDays(14), "gf_refresh", false, "Strict"),
                        new AppProperties.LoginLockout(5, Duration.ofMinutes(15))),
                new AppProperties.RateLimit(rateLimitEnabled),
                new AppProperties.Storage("./build/test-uploads"),
                new AppProperties.Bootstrap("", "", ""));
    }
}