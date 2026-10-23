package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.dto.AuthDtos.ClientInfo;
import com.garmentfactory.identity.entity.RefreshToken;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.RefreshTokenRepository;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.Tokens;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token lifecycle: issue, rotate on every use, revoke the whole family if a rotated token
 * is ever presented again (a sign it was copied), and manage a user's sessions.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    public record Issued(String rawToken, Instant expiresAt) {
    }

    public record Rotation(User user, Issued token) {
    }

    private final RefreshTokenRepository repository;
    private final AppProperties properties;
    private final Clock clock;
    private final AuditRecorder audit;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties,
                               Clock clock, AuditRecorder audit) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
        this.audit = audit;
    }

    @Transactional
    public Issued issueNewFamily(User user, ClientInfo client) {
        String raw = Tokens.newOpaqueToken();
        RefreshToken saved = persist(user, UUID.randomUUID().toString(), raw, client);
        return new Issued(raw, saved.getExpiresAt());
    }

    /**
     * ApiException must not roll back: revoking a stolen family (and its audit row) has to be
     * committed even though the caller receives a 401.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotation rotate(String rawToken, ClientInfo client) {
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(Tokens.sha256Hex(rawToken))
                .orElseThrow(RefreshTokenService::invalid);

        if (current.getRevokedAt() != null) {
            throw invalid();
        }
        if (current.getRotatedAt() != null) {
            Long userId = current.getUser().getId();
            String publicId = current.getUser().getPublicId();
            log.warn("Refresh token reuse detected, revoking family familyId={} userId={}", current.getFamilyId(), userId);
            repository.revokeFamily(current.getFamilyId(), now);
            audit.record(AuditEvent.forActor(userId, "REFRESH_TOKEN_REUSE", "USER", publicId,
                    "A rotated refresh token was presented again; the session family was revoked"));
            throw invalid();
        }
        if (!current.getExpiresAt().isAfter(now)) {
            current.setRevokedAt(now);
            throw invalid();
        }
        User user = current.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            repository.revokeFamily(current.getFamilyId(), now);
            throw invalid();
        }

        String raw = Tokens.newOpaqueToken();
        RefreshToken next = persist(user, current.getFamilyId(), raw, client);
        current.setRotatedAt(now);
        current.setReplacedById(next.getId());
        return new Rotation(user, new Issued(raw, next.getExpiresAt()));
    }

    /** Ends the session this token belongs to. Unknown tokens are ignored (logout is idempotent). */
    @Transactional
    public void revokeFamilyOf(String rawToken) {
        repository.findByTokenHash(Tokens.sha256Hex(rawToken))
                .ifPresent(token -> repository.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public Optional<String> familyOf(String rawToken) {
        return repository.findFamilyIdByTokenHash(Tokens.sha256Hex(rawToken));
    }

    @Transactional(readOnly = true)
    public List<RefreshToken> activeSessions(Long userId) {
        return repository.findActiveByUser(userId, clock.instant());
    }

    /** Returns how many rows changed; 0 means no such live session belongs to this user. */
    @Transactional
    public int revokeFamilyForUser(Long userId, String familyId) {
        return repository.revokeFamilyForUser(userId, familyId, clock.instant());
    }

    @Transactional
    public int revokeAllExcept(Long userId, String keepFamilyId) {
        return repository.revokeAllExceptFamily(userId, keepFamilyId, clock.instant());
    }

    private RefreshToken persist(User user, String familyId, String raw, ClientInfo client) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash(Tokens.sha256Hex(raw));
        token.setDeviceInfo(client.userAgent());
        token.setIp(client.ip());
        token.setExpiresAt(clock.instant().plus(properties.security().refreshToken().ttl()));
        return repository.save(token);
    }

    private static ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "The session is no longer valid. Please sign in again.");
    }
}