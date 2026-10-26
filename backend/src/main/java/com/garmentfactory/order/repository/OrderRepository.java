package com.garmentfactory.order.repository;

import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /** Candidates for the expiry sweeper; each is re-checked under a lock before it is cancelled. */
    List<Order> findByOrderStatusAndPaymentMethodAndPaymentDueAtBefore(OrderStatus orderStatus,
                                                                       PaymentMethod paymentMethod,
                                                                       Instant before, Pageable pageable);

    long countByUserIdAndPaymentMethodAndPaymentStatusAndOrderStatusIn(Long userId, PaymentMethod paymentMethod,
                                                                       PaymentStatus paymentStatus,
                                                                       Collection<OrderStatus> orderStatuses);
}