package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.dto.AuthDtos.ClientInfo;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.util.Emails;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Login, refresh and logout. */
@Service
public class AuthService {

    public record AuthResult(AccessTokenService.AccessToken accessToken,
                             RefreshTokenService.Issued refreshToken,
                             AuthenticatedUser user) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;
    private final UserAuthorityService authorities;
    private final AppProperties properties;
    private final Clock clock;
    private final AuditRecorder audit;

    /** Hash of a throwaway password, checked for unknown emails so response time does not reveal them. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
                       RefreshTokenService refreshTokens, UserAuthorityService authorities,
                       AppProperties properties, Clock clock, AuditRecorder audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.authorities = authorities;
        this.properties = properties;
        this.clock = clock;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("timing-equalisation-password");
    }

    /**
     * ApiException must not roll back: failed-attempt counters and lockouts have to be committed
     * even though the caller receives an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(String rawEmail, String password, ClientInfo client) {
        Instant now = clock.instant();
        Optional<User> found = users.findByEmail(Emails.normalize(rawEmail));
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw invalidCredentials();
        }

        User user = found.get();
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(now);
        boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
        if (locked) {
            throw invalidCredentials();
        }
        if (!passwordMatches) {
            recordFailedAttempt(user, now);
            throw invalidCredentials();
        }

        switch (user.getStatus()) {
            case PENDING_VERIFICATION -> throw ApiException.forbidden("EMAIL_NOT_VERIFIED",
                    "Please verify your email address before signing in.");
            case BLOCKED, DISABLED -> throw ApiException.forbidden("ACCOUNT_BLOCKED",
                    "This account cannot sign in. Please contact support.");
            case ACTIVE -> {
                // proceed
            }
        }

        AuthResult result = new AuthResult(
                accessTokens.issue(user.getPublicId()),
                refreshTokens.issueNewFamily(user, client),
                authorities.principalFor(user));
        users.recordSuccessfulLogin(user.getId(), now);
        return result;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "The session is no longer valid. Please sign in again.");
        }
        RefreshTokenService.Rotation rotation = refreshTokens.rotate(rawRefreshToken, client);
        User user = rotation.user();
        return new AuthResult(accessTokens.issue(user.getPublicId()), rotation.token(), authorities.principalFor(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revokeFamilyOf(rawRefreshToken);
        }
    }

    private void recordFailedAttempt(User user, Instant now) {
        AppProperties.LoginLockout lockout = properties.security().loginLockout();
        Long userId = user.getId();
        String publicId = user.getPublicId(); // read before the bulk updates below clear the persistence context
        users.resetExpiredLock(userId, now);
        users.incrementFailedLogins(userId);
        if (users.findFailedLoginCount(userId) >= lockout.maxAttempts()) {
            users.lockUntil(userId, now.plus(lockout.lockDuration()));
            audit.record(AuditEvent.forActor(userId, "ACCOUNT_LOCKED", "USER", publicId,
                    "Locked for " + lockout.lockDuration() + " after repeated failed logins"));
        }
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password.");
    }
}