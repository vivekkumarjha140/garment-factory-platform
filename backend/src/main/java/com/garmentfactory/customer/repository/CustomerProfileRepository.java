package com.garmentfactory.customer.repository;

import com.garmentfactory.customer.entity.CustomerProfile;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

    Optional<CustomerProfile> findByUserId(Long userId);

    /** Row lock (SELECT ... FOR UPDATE): serialises every change a single customer makes to their addresses. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CustomerProfile p where p.userId = :userId")
    Optional<CustomerProfile> lockByUserId(@Param("userId") Long userId);

    /** Race-free "create if missing": two requests at once end up with exactly one row. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO customer_profiles (user_id) VALUES (:userId) ON DUPLICATE KEY UPDATE user_id = user_id",
            nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId);
}