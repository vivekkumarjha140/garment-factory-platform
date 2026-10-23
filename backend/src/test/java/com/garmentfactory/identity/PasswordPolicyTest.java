package com.garmentfactory.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.identity.service.PasswordPolicy;
import com.garmentfactory.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("Str0ngPassw0rd", "user@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsTooShort() {
        assertThatThrownBy(() -> policy.validate("Short1", "user@example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("at least 10");
    }

    @Test
    void rejectsTooLong() {
        assertThatThrownBy(() -> policy.validate("a1".repeat(33), "user@example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("at most 64");
    }

    @Test
    void rejectsMultiByteInputBeyondBcryptLimit() {
        // 40 characters but 120 UTF-8 bytes: within the character limit, over the BCrypt byte limit.
        assertThatThrownBy(() -> policy.validate("あ".repeat(39) + "1", "user@example.com"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void requiresLetterAndDigit() {
        assertThatThrownBy(() -> policy.validate("onlyletterspassword", "user@example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("letter and one digit");
        assertThatThrownBy(() -> policy.validate("1234567890123", "user@example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("letter and one digit");
    }

    @Test
    void rejectsPasswordEqualToEmail() {
        assertThatThrownBy(() -> policy.validate("User1234567@Example.com", "user1234567@example.com"))
                .isInstanceOf(ApiException.class).hasMessageContaining("same as the email");
    }
}