package com.garmentfactory.payment.controller;

import com.garmentfactory.payment.dto.PaymentDtos.InitiatePaymentResponse;
import com.garmentfactory.payment.dto.PaymentDtos.SyncRequest;
import com.garmentfactory.payment.dto.PaymentDtos.SyncResponse;
import com.garmentfactory.payment.service.PaymentService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/orders/{orderNumber}/initiate")
    public InitiatePaymentResponse initiate(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable String orderNumber) {
        return service.initiate(user.id(), orderNumber);
    }

    @PostMapping("/orders/{orderNumber}/sync")
    public SyncResponse sync(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                             @Valid @RequestBody SyncRequest request) {
        return service.sync(user.id(), orderNumber, request.razorpayPaymentId());
    }
}