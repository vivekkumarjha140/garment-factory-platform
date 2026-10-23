package com.garmentfactory.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

/** Requests and responses of the staff-facing administration API. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserResponse(String publicId, String email, String fullName, String phone, String status,
                                    Set<String> roles, Instant emailVerifiedAt, Instant lastLoginAt, Instant createdAt) {
    }

    public record CreateStaffRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotEmpty @Size(max = 11) Set<@NotBlank @Size(max = 50) String> roles) {
    }

    public record UpdateRolesRequest(@NotEmpty @Size(max = 11) Set<@NotBlank @Size(max = 50) String> roles) {
    }

    public record BlockRequest(@Size(max = 300) String reason) {
    }

    public record RoleResponse(String code, String name, String description, boolean system, Set<String> permissions) {
    }

    public record PermissionResponse(String code, String description) {
    }

    /** May be empty: that removes every permission from the role. */
    public record UpdatePermissionsRequest(@NotNull @Size(max = 100) Set<@NotBlank @Size(max = 80) String> permissions) {
    }
}