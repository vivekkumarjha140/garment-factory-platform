package com.garmentfactory.order.repository;

import com.garmentfactory.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    /** Scoped to the owner: someone else's order number simply is not found. */
    Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

    /** Row lock until the transaction ends. Always take this lock BEFORE any payment row lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderNumber = :orderNumber and o.userId = :userId")
    Optional<Order> lockByOrderNumberAndUserId(@Param("orderNumber") String orderNumber, @Param("userId") Long userId);
}