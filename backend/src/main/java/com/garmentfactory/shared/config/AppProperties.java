package com.garmentfactory.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed application settings bound from the {@code app.*} namespace.
 * Validation runs at startup, so a missing required value fails fast instead of at first use.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String frontendBaseUrl,
        @NotNull @Valid Cors cors,
        @NotNull @Valid Mail mail,
        @NotNull @Valid Security security,
        @NotNull @Valid RateLimit rateLimit,
        @NotNull @Valid Storage storage,
        @Valid Bootstrap bootstrap) {

    /** Browser origins allowed to call the API (production must list https origins only). */
    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
    }

    /** Where uploaded files are stored (a mounted volume in Docker). */
    public record Storage(@NotBlank String localRoot) {
    }

    public record Mail(@NotBlank String from) {
    }

    public record Security(
            @NotNull @Valid Jwt jwt,
            @NotNull @Valid RefreshTokenProperties refreshToken,
            @NotNull @Valid LoginLockout loginLockout) {
    }

    /** Base64-encoded HMAC key (at least 32 bytes once decoded). */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @NotNull Duration accessTtl) {
        @Override
        public String toString() {
            return "Jwt[issuer=" + issuer + ", accessTtl=" + accessTtl + "]";
        }
    }

    public record RefreshTokenProperties(
            @NotNull Duration ttl,
            @NotBlank String cookieName,
            boolean cookieSecure,
            @NotBlank String cookieSameSite) {
    }

    public record LoginLockout(@Min(1) int maxAttempts, @NotNull Duration lockDuration) {
    }

    public record RateLimit(boolean enabled) {
    }

    /** Optional first administrator, created at startup only when no ADMIN exists. */
    public record Bootstrap(String adminEmail, String adminPassword, String adminName) {
        @Override
        public String toString() {
            return "Bootstrap[adminEmail=" + adminEmail + "]";
        }
    }
}