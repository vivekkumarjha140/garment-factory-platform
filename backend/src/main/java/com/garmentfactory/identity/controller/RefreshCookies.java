package com.garmentfactory.identity.controller;

import com.garmentfactory.shared.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Builds and reads the refresh-token cookie: HttpOnly, SameSite, and scoped to the auth path. */
@Component
class RefreshCookies {

    private static final String PATH = "/api/v1/auth";

    private final AppProperties properties;

    RefreshCookies(AppProperties properties) {
        this.properties = properties;
    }

    ResponseCookie issue(String value) {
        return build(value, properties.security().refreshToken().ttl());
    }

    ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String name = properties.security().refreshToken().cookieName();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie build(String value, Duration maxAge) {
        AppProperties.RefreshTokenProperties config = properties.security().refreshToken();
        return ResponseCookie.from(config.cookieName(), value)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(config.cookieSameSite())
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}