package com.garmentfactory.payment.repository;

import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    /** Scalar on purpose: it must not load the payment into the session before the locks are taken. */
    @Query("select p.orderId from Payment p where p.provider = :provider and p.providerOrderId = :providerOrderId")
    Optional<Long> findOrderIdByProviderOrder(@Param("provider") PaymentProvider provider,
                                              @Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerOrderId = :providerOrderId")
    Optional<Payment> lockByProviderOrder(@Param("provider") PaymentProvider provider,
                                          @Param("providerOrderId") String providerOrderId);
}