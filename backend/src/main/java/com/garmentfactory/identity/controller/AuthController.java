package com.garmentfactory.identity.controller;

import com.garmentfactory.identity.dto.AuthDtos.AuthResponse;
import com.garmentfactory.identity.dto.AuthDtos.ClientInfo;
import com.garmentfactory.identity.dto.AuthDtos.EmailRequest;
import com.garmentfactory.identity.dto.AuthDtos.LoginRequest;
import com.garmentfactory.identity.dto.AuthDtos.MessageResponse;
import com.garmentfactory.identity.dto.AuthDtos.RegisterRequest;
import com.garmentfactory.identity.dto.AuthDtos.ResetPasswordRequest;
import com.garmentfactory.identity.dto.AuthDtos.TokenRequest;
import com.garmentfactory.identity.dto.AuthDtos.UserSummary;
import com.garmentfactory.identity.service.AuthService;
import com.garmentfactory.identity.service.PasswordResetService;
import com.garmentfactory.identity.service.RateLimitGuard;
import com.garmentfactory.identity.service.RegistrationService;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.exception.ApiException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public authentication endpoints. Business rules live in the services; this class handles HTTP
 * concerns only: rate limits, the refresh-token cookie, the CSRF guard and response codes.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final String CSRF_HEADER = "X-Requested-With";
    private static final String CSRF_VALUE = "XMLHttpRequest";

    private static final MessageResponse REGISTER_ACCEPTED =
            new MessageResponse("If this email can be registered, a verification email has been sent.");
    private static final MessageResponse RESEND_ACCEPTED =
            new MessageResponse("If the account exists and is not yet verified, a new verification email has been sent.");
    private static final MessageResponse RESET_ACCEPTED =
            new MessageResponse("If an account exists for this email, a password reset email has been sent.");

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final PasswordResetService passwordResetService;
    private final RateLimitGuard rateLimits;
    private final RefreshCookies refreshCookies;
    private final AppProperties properties;

    public AuthController(AuthService authService, RegistrationService registrationService,
                          PasswordResetService passwordResetService, RateLimitGuard rateLimits,
                          RefreshCookies refreshCookies, AppProperties properties) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.passwordResetService = passwordResetService;
        this.rateLimits = rateLimits;
        this.refreshCookies = refreshCookies;
        this.properties = properties;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        rateLimits.register(ClientInfo.from(http).ip());
        registrationService.register(request);
        return REGISTER_ACCEPTED;
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@Valid @RequestBody TokenRequest request) {
        registrationService.verifyEmail(request.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimits.resendVerification(ClientInfo.from(http).ip(), request.email());
        registrationService.resendVerification(request.email());
        return RESEND_ACCEPTED;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        ClientInfo client = ClientInfo.from(http);
        rateLimits.login(client.ip(), request.email());
        return respond(authService.login(request.email(), request.password(), client));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        requireTrustedBrowserRequest(http);
        ClientInfo client = ClientInfo.from(http);
        rateLimits.refresh(client.ip());
        return respond(authService.refresh(refreshCookies.read(http), client));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        requireTrustedBrowserRequest(http);
        authService.logout(refreshCookies.read(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest request, HttpServletRequest http) {
        rateLimits.passwordRecovery(ClientInfo.from(http).ip(), request.email());
        passwordResetService.requestReset(request.email());
        return RESET_ACCEPTED;
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        rateLimits.resetPassword(ClientInfo.from(http).ip());
        passwordResetService.reset(request.token(), request.newPassword());
    }

    private ResponseEntity<AuthResponse> respond(AuthService.AuthResult result) {
        AuthResponse body = new AuthResponse(
                result.accessToken().value(), "Bearer", result.accessToken().expiresInSeconds(),
                UserSummary.from(result.user()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.issue(result.refreshToken().rawToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    /**
     * The refresh cookie is sent automatically by browsers, so these endpoints demand something a
     * cross-site form cannot produce: a custom header, and (when present) an allowed Origin.
     */
    private void requireTrustedBrowserRequest(HttpServletRequest request) {
        if (!CSRF_VALUE.equals(request.getHeader(CSRF_HEADER))) {
            throw ApiException.forbidden("CSRF_HEADER_MISSING", "Missing required request header.");
        }
        String origin = request.getHeader("Origin");
        if (origin != null && !properties.cors().allowedOrigins().contains(origin)) {
            throw ApiException.forbidden("ORIGIN_NOT_ALLOWED", "This origin is not allowed.");
        }
    }
}