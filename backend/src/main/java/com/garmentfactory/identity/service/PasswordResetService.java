package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.entity.TokenPurpose;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.util.AfterCommit;
import com.garmentfactory.shared.util.Emails;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot-password and reset. A successful reset signs the user out everywhere. It also activates an
 * invited account that was still pending: the emailed link proves the user owns the address.
 */
@Service
public class PasswordResetService {

    private static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserRepository users;
    private final VerificationTokenService verificationTokens;
    private final RefreshTokenService refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailSender emailSender;
    private final AuthEmails emails;
    private final AuditRecorder audit;
    private final Clock clock;

    public PasswordResetService(UserRepository users, VerificationTokenService verificationTokens,
                                RefreshTokenService refreshTokens, PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy, EmailSender emailSender, AuthEmails emails,
                                AuditRecorder audit, Clock clock) {
        this.users = users;
        this.verificationTokens = verificationTokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailSender = emailSender;
        this.emails = emails;
        this.audit = audit;
        this.clock = clock;
    }

    /** Silent when the email is unknown or the account is not active. */
    @Transactional
    public void requestReset(String rawEmail) {
        users.findByEmail(Emails.normalize(rawEmail))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .ifPresent(user -> {
                    VerificationTokenService.Issued issued =
                            verificationTokens.issue(user, TokenPurpose.PASSWORD_RESET, RESET_TTL);
                    String to = user.getEmail();
                    String name = user.getFullName();
                    AfterCommit.run(() -> emailSender.send(emails.passwordReset(to, name, issued.rawToken())));
                });
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        Long userId = verificationTokens.peekUserId(rawToken, TokenPurpose.PASSWORD_RESET)
                .orElseThrow(PasswordResetService::invalidToken);
        User user = users.findById(userId).orElseThrow(PasswordResetService::invalidToken);
        String email = user.getEmail();
        String name = user.getFullName();
        String publicId = user.getPublicId();

        // Validate the new password before the token is burned, so a typo does not waste the link.
        passwordPolicy.validate(newPassword, email);
        verificationTokens.consume(rawToken, TokenPurpose.PASSWORD_RESET)
                .orElseThrow(PasswordResetService::invalidToken);

        users.updatePassword(userId, passwordEncoder.encode(newPassword));
        users.activatePending(userId, clock.instant());
        refreshTokens.revokeAllForUser(userId);
        audit.record(AuditEvent.forActor(userId, "PASSWORD_RESET_COMPLETED", "USER", publicId,
                "Password set by email link; all sessions revoked"));
        AfterCommit.run(() -> emailSender.send(emails.passwordChanged(email, name)));
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN", "This link is invalid or has expired.");
    }
}