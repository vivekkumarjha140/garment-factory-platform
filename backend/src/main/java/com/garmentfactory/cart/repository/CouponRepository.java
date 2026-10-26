package com.garmentfactory.cart.repository;

import com.garmentfactory.cart.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Column collation is *_ai_ci, but codes are stored upper-case; callers should normalise too. */
    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomic redemption counter (same idea as stock reservation).
     * Returns 1 on success, 0 if the total usage limit is already exhausted or coupon is inactive.
     */
    @Modifying
    @Query("""
            update Coupon c
               set c.timesUsed = c.timesUsed + 1, c.version = c.version + 1
             where c.id = :id
               and c.active = true
               and (c.usageLimitTotal is null or c.timesUsed < c.usageLimitTotal)
            """)
    int incrementUsage(@Param("id") Long id);

    /** Gives one redemption back (cancelled or expired unpaid order). Never goes below zero. */
    @Modifying
    @Query("""
            update Coupon c
               set c.timesUsed = c.timesUsed - 1, c.version = c.version + 1
             where c.id = :id and c.timesUsed > 0
            """)
    int decrementUsage(@Param("id") Long id);
}