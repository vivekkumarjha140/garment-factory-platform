package com.garmentfactory.identity.repository;

import com.garmentfactory.identity.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Row lock: two concurrent refreshes of the same token are serialized, the second sees it as rotated. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Lock-free lookup used only to tell which session a cookie belongs to. */
    @Query("select t.familyId from RefreshToken t where t.tokenHash = :hash")
    Optional<String> findFamilyIdByTokenHash(@Param("hash") String hash);

    /** The live token of each session: not rotated, not revoked, not expired. */
    @Query("select t from RefreshToken t where t.user.id = :userId and t.revokedAt is null "
            + "and t.rotatedAt is null and t.expiresAt > :now order by t.createdAt desc")
    List<RefreshToken> findActiveByUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

    /** Scoped to the owner, so one user can never end another user's session. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.familyId = :familyId and t.revokedAt is null")
    int revokeFamilyForUser(@Param("userId") Long userId, @Param("familyId") String familyId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.user.id = :userId and t.familyId <> :keepFamilyId and t.revokedAt is null")
    int revokeAllExceptFamily(@Param("userId") Long userId, @Param("keepFamilyId") String keepFamilyId,
                              @Param("now") Instant now);
}