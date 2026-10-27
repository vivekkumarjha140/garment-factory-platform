package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Invoice;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.repository.InvoiceRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.shared.exception.ApiException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AdminOrderQueryService {

    private final OrderRepository orders;
    private final OrderQueryService queries;
    private final InvoiceRepository invoices;

    public AdminOrderQueryService(OrderRepository orders, OrderQueryService queries, InvoiceRepository invoices) {
        this.orders = orders;
        this.queries = queries;
        this.invoices = invoices;
    }

    /** Newest first. size is clamped to 1..100. */
    public Page<OrderSummaryView> search(AdminOrderFilter filter, int page, int size) {
        return queries.summarize(orders.findAll(matching(filter),
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "placedAt", "id"))));
    }

    public AdminOrderView get(String orderNumber) {
        Order order = orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
        return view(queries.details(order));
    }

    public AdminOrderView view(OrderDetails details) {
        String invoiceNumber = invoices.findByOrderId(details.order().getId()).map(Invoice::getInvoiceNumber).orElse(null);
        return new AdminOrderView(details, invoiceNumber);
    }

    private static Specification<Order> matching(AdminOrderFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> all = new ArrayList<>();
            if (filter.orderStatus() != null) {
                all.add(cb.equal(root.get("orderStatus"), filter.orderStatus()));
            }
            if (filter.paymentStatus() != null) {
                all.add(cb.equal(root.get("paymentStatus"), filter.paymentStatus()));
            }
            if (filter.shipmentStatus() != null) {
                all.add(cb.equal(root.get("shipmentStatus"), filter.shipmentStatus()));
            }
            if (filter.paymentMethod() != null) {
                all.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + escapeLike(filter.q().trim().toLowerCase(Locale.ROOT)) + "%";
                all.add(cb.or(
                        cb.like(cb.lower(root.<String>get("orderNumber")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("customerEmail")), like, '\\'),
                        cb.like(cb.lower(root.<String>get("customerName")), like, '\\')));
            }
            return cb.and(all.toArray(new Predicate[0]));
        };
    }

    /** A typed % or _ must match itself, not act as a wildcard. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}