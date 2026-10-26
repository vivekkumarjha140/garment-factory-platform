package com.garmentfactory.payment.repository;

import com.garmentfactory.payment.entity.Payment;
import com.garmentfactory.payment.entity.PaymentProvider;
import com.garmentfactory.payment.entity.PaymentRecordStatus;
import com.garmentfactory.payment.entity.PaymentType;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findFirstByOrderIdAndPaymentTypeOrderByIdDesc(Long orderId, PaymentType paymentType);

    Optional<Payment> findFirstByOrderIdAndPaymentTypeAndStatus(Long orderId, PaymentType paymentType,
                                                                PaymentRecordStatus status);

    List<Payment> findByPaymentTypeAndStatusInAndUpdatedAtBefore(PaymentType paymentType,
                                                                 Collection<PaymentRecordStatus> statuses,
                                                                 Instant before);

    /** Scalar on purpose: it must not load the payment into the session before the locks are taken. */
    @Query("select p.orderId from Payment p where p.provider = :provider and p.providerOrderId = :providerOrderId")
    Optional<Long> findOrderIdByProviderOrder(@Param("provider") PaymentProvider provider,
                                              @Param("providerOrderId") String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.provider = :provider and p.providerOrderId = :providerOrderId")
    Optional<Payment> lockByProviderOrder(@Param("provider") PaymentProvider provider,
                                          @Param("providerOrderId") String providerOrderId);
}