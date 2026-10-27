package com.garmentfactory.order.service;

import com.garmentfactory.notification.api.EmailMessage;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.order.api.OrderShipmentEvent;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.config.BusinessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells the customer when their order has shipped and when it was delivered. Never throws. */
@Component
public class ShipmentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(ShipmentNotificationListener.class);

    private final OrderRepository orders;
    private final EmailSender emailSender;
    private final AppProperties app;
    private final BusinessProperties business;

    public ShipmentNotificationListener(OrderRepository orders, EmailSender emailSender, AppProperties app,
                                        BusinessProperties business) {
        this.orders = orders;
        this.emailSender = emailSender;
        this.app = app;
        this.business = business;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShipment(OrderShipmentEvent event) {
        try {
            Order order = orders.findById(event.orderId()).orElseThrow();
            String link = app.frontendBaseUrl() + "/account/orders/" + order.getOrderNumber();
            boolean delivered = "DELIVERED".equals(event.status());
            String subject = "Order " + order.getOrderNumber() + (delivered ? " was delivered" : " has shipped");
            String body = "Hi " + order.getCustomerName() + ",\n\n"
                    + (delivered
                    ? "Your order " + order.getOrderNumber() + " was delivered. Thank you for shopping with us!\n\n"
                      + "You can download your tax invoice from your order page:\n"
                    : "Good news: your order " + order.getOrderNumber() + " is on its way.\n\nTrack it here:\n")
                    + link + "\n\n" + business.name() + "\n";
            emailSender.send(new EmailMessage(order.getCustomerEmail(), subject, body));
        } catch (RuntimeException e) {
            log.error("Shipment email failed for order id {}", event.orderId(), e);
        }
    }
}