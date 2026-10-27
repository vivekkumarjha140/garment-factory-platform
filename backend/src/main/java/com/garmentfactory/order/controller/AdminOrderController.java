package com.garmentfactory.order.controller;

import com.garmentfactory.order.dto.AdminOrderDtos.AdminOrderResponse;
import com.garmentfactory.order.dto.AdminOrderDtos.AdminOrderSummaryResponse;
import com.garmentfactory.order.dto.AdminOrderDtos.DeliverRequest;
import com.garmentfactory.order.dto.AdminOrderDtos.NoteRequest;
import com.garmentfactory.order.dto.AdminOrderDtos.StaffCancelRequest;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentMethod;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.ShipmentStatus;
import com.garmentfactory.order.mapper.OrderMapper;
import com.garmentfactory.order.service.AdminOrderFilter;
import com.garmentfactory.order.service.AdminOrderQueryService;
import com.garmentfactory.order.service.InvoiceFile;
import com.garmentfactory.order.service.InvoiceService;
import com.garmentfactory.order.service.OrderCancellationService;
import com.garmentfactory.order.service.OrderDetails;
import com.garmentfactory.order.service.OrderFulfilmentService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Staff view of all orders. Reading needs ORDER_VIEW, moving an order along ORDER_FULFIL, cancelling ORDER_CANCEL. */
@RestController
@RequestMapping("/api/v1/admin/orders")
@Tag(name = "Admin orders")
public class AdminOrderController {

    private final AdminOrderQueryService queries;
    private final OrderFulfilmentService fulfilment;
    private final OrderCancellationService cancellation;
    private final InvoiceService invoices;

    public AdminOrderController(AdminOrderQueryService queries, OrderFulfilmentService fulfilment,
                                OrderCancellationService cancellation, InvoiceService invoices) {
        this.queries = queries;
        this.fulfilment = fulfilment;
        this.cancellation = cancellation;
        this.invoices = invoices;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public PageResponse<AdminOrderSummaryResponse> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) OrderStatus orderStatus,
                                                        @RequestParam(required = false) PaymentStatus paymentStatus,
                                                        @RequestParam(required = false) ShipmentStatus shipmentStatus,
                                                        @RequestParam(required = false) PaymentMethod paymentMethod,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(queries.search(new AdminOrderFilter(q, orderStatus, paymentStatus, shipmentStatus, paymentMethod),
                page, size).map(OrderMapper::toAdminSummary));
    }

    @GetMapping("/{orderNumber}")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public AdminOrderResponse get(@PathVariable String orderNumber) {
        return OrderMapper.toAdmin(queries.get(orderNumber));
    }

    @GetMapping("/{orderNumber}/invoice")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<byte[]> invoice(@PathVariable String orderNumber) {
        InvoiceFile file = invoices.fileForStaff(orderNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(file.content());
    }

    @PostMapping("/{orderNumber}/start-processing")
    @PreAuthorize("hasAuthority('ORDER_FULFIL')")
    public AdminOrderResponse startProcessing(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                              @Valid @RequestBody(required = false) NoteRequest request) {
        return respond(fulfilment.startProcessing(user.id(), orderNumber, request == null ? null : request.note()));
    }

    @PostMapping("/{orderNumber}/pack")
    @PreAuthorize("hasAuthority('ORDER_FULFIL')")
    public AdminOrderResponse pack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                   @Valid @RequestBody(required = false) NoteRequest request) {
        return respond(fulfilment.pack(user.id(), orderNumber, request == null ? null : request.note()));
    }

    @PostMapping("/{orderNumber}/ship")
    @PreAuthorize("hasAuthority('ORDER_FULFIL')")
    public AdminOrderResponse ship(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                   @Valid @RequestBody(required = false) NoteRequest request) {
        return respond(fulfilment.ship(user.id(), orderNumber, request == null ? null : request.note()));
    }

    @PostMapping("/{orderNumber}/deliver")
    @PreAuthorize("hasAuthority('ORDER_FULFIL')")
    public AdminOrderResponse deliver(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                      @Valid @RequestBody(required = false) DeliverRequest request) {
        return respond(fulfilment.deliver(user.id(), orderNumber, request != null && request.cashCollected(),
                request == null ? null : request.note()));
    }

    @PostMapping("/{orderNumber}/cancel")
    @PreAuthorize("hasAuthority('ORDER_CANCEL')")
    public AdminOrderResponse cancel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable String orderNumber,
                                     @Valid @RequestBody StaffCancelRequest request) {
        return respond(cancellation.cancelByStaff(user.id(), orderNumber, request.reason()));
    }

    private AdminOrderResponse respond(OrderDetails details) {
        return OrderMapper.toAdmin(queries.view(details));
    }
}