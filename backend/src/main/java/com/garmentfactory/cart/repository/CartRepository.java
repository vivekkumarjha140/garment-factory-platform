package com.garmentfactory.cart.repository;

import com.garmentfactory.cart.entity.Cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByUserId(Long userId);

    /** Plain existence check: loads no entity and takes no lock. */
    boolean existsByUserId(Long userId);

    /** Row lock: serialises all writes of one user's cart (no duplicate lines on double-click). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId")
    Optional<Cart> lockByUserId(@Param("userId") Long userId);

    /**
     * Race-free create. The ON DUPLICATE KEY form takes an EXCLUSIVE lock on the row (new or existing),
     * so parallel first-adds queue up instead of deadlocking on gap locks.
     */
    @Modifying
    @Query(value = """
            INSERT INTO carts (user_id, version, created_at, updated_at)
            VALUES (:userId, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
            ON DUPLICATE KEY UPDATE user_id = user_id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId);
}