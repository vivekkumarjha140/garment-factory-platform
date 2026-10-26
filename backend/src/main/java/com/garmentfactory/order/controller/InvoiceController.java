package com.garmentfactory.order.controller;

import com.garmentfactory.order.service.InvoiceFile;
import com.garmentfactory.order.service.InvoiceService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    /** The signed-in customer's own invoice as a PDF. 409 INVOICE_NOT_AVAILABLE until the order is paid and confirmed. */
    @GetMapping("/{orderNumber}/invoice")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber) {
        InvoiceFile file = invoices.fileForUser(user.id(), orderNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.content());
    }
}