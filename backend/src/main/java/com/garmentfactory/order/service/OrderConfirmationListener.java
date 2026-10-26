package com.garmentfactory.order.service;

import com.garmentfactory.notification.api.EmailMessage;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.order.api.OrderConfirmedEvent;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.config.BusinessProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After a confirmation has committed: issue the invoice (only for paid orders; a cash-on-delivery order is
 * invoiced when its payment is collected), then email the customer. Runs off the request thread and never
 * throws, so a slow mail server or a PDF problem can never undo or delay a confirmed order.
 */
@Component
public class OrderConfirmationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderConfirmationListener.class);

    private final InvoiceService invoices;
    private final OrderRepository orders;
    private final EmailSender emailSender;
    private final AppProperties app;
    private final BusinessProperties business;

    public OrderConfirmationListener(InvoiceService invoices, OrderRepository orders, EmailSender emailSender,
                                     AppProperties app, BusinessProperties business) {
        this.invoices = invoices;
        this.orders = orders;
        this.emailSender = emailSender;
        this.app = app;
        this.business = business;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        Order order;
        try {
            order = orders.findById(event.orderId()).orElseThrow();
        } catch (RuntimeException e) {
            log.error("Confirmation could not load order id {}", event.orderId(), e);
            return;
        }
        String invoiceNumber = null;
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            try {
                invoiceNumber = invoices.ensureForOrder(order.getId()).getInvoiceNumber();
            } catch (RuntimeException e) {
                log.error("Invoice could not be issued for order id {}; it will be created on first download", event.orderId(), e);
            }
        }
        try {
            emailSender.send(confirmationEmail(order, invoiceNumber));
        } catch (RuntimeException e) {
            log.error("Confirmation email failed for order id {}", event.orderId(), e);
        }
    }

    private EmailMessage confirmationEmail(Order order, String invoiceNumber) {
        String link = app.frontendBaseUrl() + "/account/orders/" + order.getOrderNumber();
        StringBuilder body = new StringBuilder().append("Hi ").append(order.getCustomerName()).append(",\n\n");
        if (order.getPaymentMethod() == PaymentMethod.COD) {
            body.append("Thank you for your order. Your order ").append(order.getOrderNumber())
                    .append(" is confirmed. You chose cash on delivery, so please keep Rs. ")
                    .append(order.getGrandTotal().toPlainString()).append(" ready when it arrives.\n\n");
        } else {
            body.append("Thank you for your order. We have received your payment of Rs. ")
                    .append(order.getGrandTotal().toPlainString()).append(" for order ").append(order.getOrderNumber())
                    .append(", and it is now confirmed.\n\n");
        }
        if (invoiceNumber != null) {
            body.append("Tax invoice: ").append(invoiceNumber).append("\n");
        }
        body.append("View your order").append(invoiceNumber != null ? " and download the invoice" : "").append(" here:\n")
                .append(link).append("\n\n")
                .append("We will email you again when your order ships.\n\n")
                .append(business.name()).append("\n");
        return new EmailMessage(order.getCustomerEmail(), "Order " + order.getOrderNumber() + " confirmed", body.toString());
    }
}