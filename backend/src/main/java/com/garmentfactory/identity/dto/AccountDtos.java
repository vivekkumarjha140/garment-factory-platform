package com.garmentfactory.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Requests and responses of the signed-in user's own account endpoints. */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** {@code id} is the session identifier to pass to DELETE /sessions/{id}. */
    public record SessionResponse(String id, String userAgent, String ip, Instant lastActiveAt, boolean current) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 64) String currentPassword,
            @NotBlank @Size(max = 64) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[]";
        }
    }
}