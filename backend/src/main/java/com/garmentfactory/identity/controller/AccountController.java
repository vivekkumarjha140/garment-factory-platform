package com.garmentfactory.identity.controller;

import com.garmentfactory.identity.dto.AccountDtos.ChangePasswordRequest;
import com.garmentfactory.identity.dto.AccountDtos.SessionResponse;
import com.garmentfactory.identity.service.AccountService;
import com.garmentfactory.identity.service.RateLimitGuard;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Signed-in user's sessions and password. These sit under /auth only because the refresh cookie
 * (needed to mark the "current" session) is scoped to that path. All require a Bearer token.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Account sessions")
public class AccountController {

    private final AccountService accountService;
    private final RateLimitGuard rateLimits;
    private final RefreshCookies refreshCookies;

    public AccountController(AccountService accountService, RateLimitGuard rateLimits, RefreshCookies refreshCookies) {
        this.accountService = accountService;
        this.rateLimits = rateLimits;
        this.refreshCookies = refreshCookies;
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal AuthenticatedUser user, HttpServletRequest http) {
        return accountService.listSessions(user.id(), refreshCookies.read(http)).stream()
                .map(view -> new SessionResponse(view.id(), view.userAgent(), view.ip(), view.lastActiveAt(), view.current()))
                .toList();
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String sessionId) {
        accountService.revokeSession(user, sessionId);
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal AuthenticatedUser user) {
        accountService.logoutAll(user);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody ChangePasswordRequest request, HttpServletRequest http) {
        rateLimits.changePassword(user.id());
        accountService.changePassword(user, request.currentPassword(), request.newPassword(), refreshCookies.read(http));
    }
}