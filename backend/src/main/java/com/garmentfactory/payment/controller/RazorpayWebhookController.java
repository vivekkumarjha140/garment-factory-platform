package com.garmentfactory.payment.controller;

import com.garmentfactory.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public endpoint (no JWT). The body must stay a raw String: the signature is computed over its exact bytes. */
@Hidden
@RestController
@RequestMapping("/api/v1/payments/razorpay")
public class RazorpayWebhookController {

    private final PaymentWebhookService service;

    public RazorpayWebhookController(PaymentWebhookService service) {
        this.service = service;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(@RequestBody String body,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
                                        @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventId) {
        service.handle(body, signature, eventId);
        return ResponseEntity.ok().build();
    }
}