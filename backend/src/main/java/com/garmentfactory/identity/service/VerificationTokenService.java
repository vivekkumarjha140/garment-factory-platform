package com.garmentfactory.identity.service;

import com.garmentfactory.identity.entity.TokenPurpose;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.VerificationToken;
import com.garmentfactory.identity.repository.VerificationTokenRepository;
import com.garmentfactory.shared.security.Tokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationTokenService {

    public record Issued(String rawToken, Instant expiresAt) {
    }

    private final VerificationTokenRepository repository;
    private final Clock clock;

    public VerificationTokenService(VerificationTokenRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Issued issue(User user, TokenPurpose purpose, Duration ttl) {
        Instant now = clock.instant();
        repository.invalidateOutstanding(user.getId(), purpose, now);

        String raw = Tokens.newOpaqueToken();
        VerificationToken token = new VerificationToken();
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenHash(Tokens.sha256Hex(raw));
        token.setExpiresAt(now.plus(ttl));
        repository.save(token);
        return new Issued(raw, token.getExpiresAt());
    }

    /** Checks a token without using it, so input can be validated before the token is burned. */
    @Transactional(readOnly = true)
    public Optional<Long> peekUserId(String rawToken, TokenPurpose purpose) {
        Instant now = clock.instant();
        return repository.findByTokenHash(Tokens.sha256Hex(rawToken))
                .filter(token -> token.getPurpose() == purpose
                        && token.getUsedAt() == null
                        && token.getExpiresAt().isAfter(now))
                .map(token -> token.getUser().getId());
    }

    /** Uses the token exactly once. Empty if it is unknown, expired, used or of another purpose. */
    @Transactional
    public Optional<Long> consume(String rawToken, TokenPurpose purpose) {
        String hash = Tokens.sha256Hex(rawToken);
        if (repository.markUsed(hash, purpose, clock.instant()) != 1) {
            return Optional.empty();
        }
        return repository.findByTokenHash(hash).map(token -> token.getUser().getId());
    }
}