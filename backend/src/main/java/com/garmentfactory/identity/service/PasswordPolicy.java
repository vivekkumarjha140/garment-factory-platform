package com.garmentfactory.identity.service;

import com.garmentfactory.shared.exception.ApiException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * 10 to 64 characters with a letter and a digit. The upper bound also keeps the UTF-8 size within
 * BCrypt's 72-byte input limit, which would otherwise silently ignore (or reject) the tail.
 */
@Component
public class PasswordPolicy {

    static final int MIN_LENGTH = 10;
    static final int MAX_LENGTH = 64;
    private static final int BCRYPT_MAX_BYTES = 72;

    public void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw weak("Password must be at least " + MIN_LENGTH + " characters long.");
        }
        if (password.length() > MAX_LENGTH || password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw weak("Password must be at most " + MAX_LENGTH + " characters long.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw weak("Password must contain at least one letter and one digit.");
        }
        if (email != null && password.equalsIgnoreCase(email)) {
            throw weak("Password must not be the same as the email address.");
        }
    }

    private static ApiException weak(String message) {
        return ApiException.badRequest("WEAK_PASSWORD", message);
    }
}