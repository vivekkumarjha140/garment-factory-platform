package com.garmentfactory.identity.dto;

import com.garmentfactory.shared.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Request and response records of the authentication API. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password,
            @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "must be 7 to 15 digits, optionally starting with +") String phone) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + "]";
        }
    }

    public record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 64) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + "]";
        }
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {

        @Override
        public String toString() {
            return "TokenRequest[]";
        }
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 64) String newPassword) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[]";
        }
    }

    public record MessageResponse(String message) {
    }

    public record UserSummary(String publicId, String email, String fullName, Set<String> roles, Set<String> permissions) {

        public static UserSummary from(AuthenticatedUser user) {
            return new UserSummary(user.publicId(), user.email(), user.fullName(), user.roles(), user.permissions());
        }
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {
    }

    /** Caller details stored with a refresh token so a user can later recognise a device. */
    public record ClientInfo(String ip, String userAgent) {

        public static ClientInfo from(HttpServletRequest request) {
            String agent = request.getHeader("User-Agent");
            if (agent != null && agent.length() > 255) {
                agent = agent.substring(0, 255);
            }
            return new ClientInfo(request.getRemoteAddr(), agent);
        }
    }
}