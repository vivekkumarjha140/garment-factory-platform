package com.garmentfactory.identity.repository;

import com.garmentfactory.identity.entity.TokenPurpose;
import com.garmentfactory.identity.entity.VerificationToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    Optional<VerificationToken> findByTokenHash(String tokenHash);

    /** Issuing a new token invalidates earlier unused ones of the same purpose. */
    @Modifying(flushAutomatically = true)
    @Query("update VerificationToken t set t.usedAt = :now "
            + "where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidateOutstanding(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
                              @Param("now") Instant now);

    /** Atomic single use: exactly one caller can flip used_at from null, so a token works once. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update VerificationToken t set t.usedAt = :now "
            + "where t.tokenHash = :hash and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("hash") String hash, @Param("purpose") TokenPurpose purpose, @Param("now") Instant now);
}