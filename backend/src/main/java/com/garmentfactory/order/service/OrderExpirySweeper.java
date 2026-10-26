package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.repository.OrderRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cancels online orders that were never paid. The grace period keeps a payment that was made just in time, but
 * whose webhook is a little late, from being cancelled underneath the customer.
 */
@Component
@ConditionalOnProperty(prefix = "orders.expiry-sweeper", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpirySweeper {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirySweeper.class);

    private final OrderRepository orders;
    private final OrderCancellationService cancellation;
    private final int batchSize;
    private final Duration grace;

    public OrderExpirySweeper(OrderRepository orders, OrderCancellationService cancellation,
                              @Value("${orders.expiry-sweeper.batch-size:100}") int batchSize,
                              @Value("${orders.expiry-sweeper.grace-minutes:10}") long graceMinutes) {
        this.orders = orders;
        this.cancellation = cancellation;
        this.batchSize = batchSize;
        this.grace = Duration.ofMinutes(graceMinutes);
    }

    @Scheduled(fixedDelayString = "${orders.expiry-sweeper.interval-ms:60000}",
            initialDelayString = "${orders.expiry-sweeper.initial-delay-ms:45000}")
    public void sweep() {
        try {
            Instant cutoff = Instant.now().minus(grace);
            List<Order> due = orders.findByOrderStatusAndPaymentMethodAndPaymentDueAtBefore(
                    OrderStatus.PENDING_PAYMENT, PaymentMethod.ONLINE, cutoff,
                    PageRequest.of(0, batchSize, Sort.by("paymentDueAt")));
            int expired = 0;
            for (Order order : due) {
                try {
                    if (cancellation.expireIfStillUnpaid(order.getId(), cutoff)) {
                        expired++;
                    }
                } catch (RuntimeException e) {
                    log.error("Could not expire order id {}; will retry on the next run", order.getId(), e);
                }
            }
            if (expired > 0) {
                log.info("Order sweeper cancelled {} unpaid order(s)", expired);
            }
        } catch (RuntimeException e) {
            log.error("Order expiry sweep failed; will retry on the next run", e);
        }
    }
}