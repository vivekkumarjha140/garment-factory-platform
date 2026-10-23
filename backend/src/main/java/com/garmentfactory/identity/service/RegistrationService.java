package com.garmentfactory.identity.service;

import com.garmentfactory.identity.dto.AuthDtos.RegisterRequest;
import com.garmentfactory.identity.entity.Role;
import com.garmentfactory.identity.entity.TokenPurpose;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.RoleRepository;
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
 * Sign-up and email verification. Callers always get the same outcome whether or not the email was
 * already registered, so the endpoint cannot be used to discover accounts.
 */
@Service
public class RegistrationService {

    private static final Duration VERIFICATION_TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final VerificationTokenService verificationTokens;
    private final EmailSender emailSender;
    private final AuthEmails emails;
    private final Clock clock;

    public RegistrationService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                               PasswordPolicy passwordPolicy, VerificationTokenService verificationTokens,
                               EmailSender emailSender, AuthEmails emails, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.verificationTokens = verificationTokens;
        this.emailSender = emailSender;
        this.emails = emails;
        this.clock = clock;
    }

    @Transactional
    public void register(RegisterRequest request) {
        String email = Emails.normalize(request.email());
        passwordPolicy.validate(request.password(), email);

        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            handleExistingAccount(existing.get());
            return;
        }

        Role customer = roles.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("Seed role CUSTOMER is missing"));
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.getRoles().add(customer);
        users.save(user);

        sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Long userId = verificationTokens.consume(rawToken, TokenPurpose.EMAIL_VERIFICATION)
                .orElseThrow(() -> ApiException.badRequest("INVALID_OR_EXPIRED_TOKEN",
                        "This link is invalid or has expired."));
        users.activatePending(userId, clock.instant());
    }

    @Transactional
    public void resendVerification(String rawEmail) {
        users.findByEmail(Emails.normalize(rawEmail))
                .filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::sendVerification);
    }

    private void handleExistingAccount(User user) {
        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> sendVerification(user);
            case ACTIVE -> {
                String to = user.getEmail();
                String name = user.getFullName();
                AfterCommit.run(() -> emailSender.send(emails.alreadyRegistered(to, name)));
            }
            case BLOCKED, DISABLED -> {
                // Say nothing: the account state must not be revealed.
            }
        }
    }

    private void sendVerification(User user) {
        VerificationTokenService.Issued issued = verificationTokens.issue(user, TokenPurpose.EMAIL_VERIFICATION, VERIFICATION_TTL);
        String to = user.getEmail();
        String name = user.getFullName();
        AfterCommit.run(() -> emailSender.send(emails.verification(to, name, issued.rawToken())));
    }
}