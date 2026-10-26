package com.garmentfactory.cart.repository;

import com.garmentfactory.cart.entity.CouponRedemption;
import com.garmentfactory.cart.entity.RedemptionStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    long countByCouponIdAndUserIdAndStatus(Long couponId, Long userId, RedemptionStatus status);

    Optional<CouponRedemption> findByOrderIdAndStatus(Long orderId, RedemptionStatus status);
}