package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.util.AfterCommit;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What a signed-in user can do with their own account: sessions and password. */
@Service
public class AccountService {

    public record SessionView(String id, String userAgent, String ip, Instant lastActiveAt, boolean current) {
    }

    private final RefreshTokenService refreshTokens;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailSender emailSender;
    private final AuthEmails emails;
    private final AuditRecorder audit;

    public AccountService(RefreshTokenService refreshTokens, UserRepository users, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, EmailSender emailSender, AuthEmails emails,
                          AuditRecorder audit) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailSender = emailSender;
        this.emails = emails;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SessionView> listSessions(long userId, String currentRefreshToken) {
        String currentFamily = familyOrNull(currentRefreshToken);
        return refreshTokens.activeSessions(userId).stream()
                .map(token -> new SessionView(token.getFamilyId(), token.getDeviceInfo(), token.getIp(),
                        token.getCreatedAt(), token.getFamilyId().equals(currentFamily)))
                .toList();
    }

    @Transactional
    public void revokeSession(AuthenticatedUser actor, String sessionId) {
        if (refreshTokens.revokeFamilyForUser(actor.id(), sessionId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found.");
        }
        audit.record(AuditEvent.of("SESSION_REVOKED", "USER", actor.publicId(), "sessionId=" + sessionId));
    }

    @Transactional
    public void logoutAll(AuthenticatedUser actor) {
        refreshTokens.revokeAllForUser(actor.id());
        audit.record(AuditEvent.of("ALL_SESSIONS_REVOKED", "USER", actor.publicId(), "Signed out of every device"));
    }

    /**
     * Keeps the session that made the request and ends all others. Without a recognisable session
     * cookie every session is ended.
     */
    @Transactional
    public void changePassword(AuthenticatedUser actor, String currentPassword, String newPassword,
                               String currentRefreshToken) {
        User user = users.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "The current password is incorrect.");
        }
        if (currentPassword.equals(newPassword)) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "The new password must differ from the current one.");
        }
        passwordPolicy.validate(newPassword, user.getEmail());

        String email = user.getEmail();
        String name = user.getFullName();
        users.updatePassword(actor.id(), passwordEncoder.encode(newPassword));

        String keepFamily = familyOrNull(currentRefreshToken);
        if (keepFamily == null) {
            refreshTokens.revokeAllForUser(actor.id());
        } else {
            refreshTokens.revokeAllExcept(actor.id(), keepFamily);
        }
        audit.record(AuditEvent.of("PASSWORD_CHANGED", "USER", actor.publicId(), "Other sessions revoked"));
        AfterCommit.run(() -> emailSender.send(emails.passwordChanged(email, name)));
    }

    private String familyOrNull(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        return refreshTokens.familyOf(rawToken).orElse(null);
    }
}