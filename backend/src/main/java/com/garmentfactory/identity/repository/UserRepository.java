package com.garmentfactory.identity.repository;

import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(String publicId);

    @Query("select count(u) from User u join u.roles r where r.code = :roleCode")
    long countWithRole(@Param("roleCode") String roleCode);

    @Query("select r.code from User u join u.roles r where u.id = :userId")
    List<String> findRoleCodes(@Param("userId") Long userId);

    @Query("select distinct p.code from User u join u.roles r join r.permissions p where u.id = :userId")
    List<String> findPermissionCodes(@Param("userId") Long userId);

    @Query("select u.failedLoginCount from User u where u.id = :id")
    int findFailedLoginCount(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = u.failedLoginCount + 1 where u.id = :id")
    void incrementFailedLogins(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.lockedUntil = :until where u.id = :id")
    void lockUntil(@Param("id") Long id, @Param("until") Instant until);

    /** A lock that has run out also clears the failure counter, so users get a fresh set of attempts. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null "
            + "where u.id = :id and u.lockedUntil is not null and u.lockedUntil <= :now")
    void resetExpiredLock(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null, u.lastLoginAt = :now where u.id = :id")
    void recordSuccessfulLogin(@Param("id") Long id, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.status = :to, u.emailVerifiedAt = :now where u.id = :id and u.status = :from")
    int changeStatusAndVerify(@Param("id") Long id, @Param("now") Instant now,
                              @Param("from") UserStatus from, @Param("to") UserStatus to);

    default int activatePending(Long id, Instant now) {
        return changeStatusAndVerify(id, now, UserStatus.PENDING_VERIFICATION, UserStatus.ACTIVE);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.passwordHash = :hash, u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    void updatePassword(@Param("id") Long id, @Param("hash") String hash);

    @Query(value = "select u from User u where u.status in :statuses "
            + "and (lower(u.email) like :pattern or lower(u.fullName) like :pattern) "
            + "and (:anyRole = true or exists (select 1 from u.roles r where r.code = :roleCode)) "
            + "order by u.createdAt desc, u.id desc",
            countQuery = "select count(u) from User u where u.status in :statuses "
                    + "and (lower(u.email) like :pattern or lower(u.fullName) like :pattern) "
                    + "and (:anyRole = true or exists (select 1 from u.roles r where r.code = :roleCode))")
    Page<User> search(@Param("statuses") Collection<UserStatus> statuses, @Param("pattern") String pattern,
                      @Param("anyRole") boolean anyRole, @Param("roleCode") String roleCode, Pageable pageable);

    /** One query for the roles of a whole page of users (avoids one query per row). */
    @Query("select u.id, r.code from User u join u.roles r where u.id in :ids")
    List<Object[]> findRoleCodesForUsers(@Param("ids") Collection<Long> ids);

    /**
     * Locking read (SELECT ... FOR UPDATE) of the active members of a role. It sees the latest
     * committed data and makes concurrent admin changes queue up instead of racing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u join u.roles r where r.code = :roleCode and u.status = :status order by u.id")
    List<User> lockActiveWithRole(@Param("roleCode") String roleCode, @Param("status") UserStatus status);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update User u set u.failedLoginCount = 0, u.lockedUntil = null where u.id = :id")
    void clearLockout(@Param("id") Long id);
}