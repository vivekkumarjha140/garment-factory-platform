package com.garmentfactory.order.service;

import com.garmentfactory.order.entity.Invoice;
import com.garmentfactory.order.entity.Order;
import com.garmentfactory.order.entity.OrderItem;
import com.garmentfactory.order.entity.OrderStatus;
import com.garmentfactory.order.entity.PaymentStatus;
import com.garmentfactory.order.entity.AddressSnapshot;
import com.garmentfactory.order.repository.InvoiceRepository;
import com.garmentfactory.order.repository.OrderItemRepository;
import com.garmentfactory.order.repository.OrderRepository;
import com.garmentfactory.shared.config.BusinessProperties;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.numbering.NumberSequences;
import com.garmentfactory.storage.api.FileStorage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Issues the tax invoice of a paid, confirmed order exactly once, and serves its PDF.
 * Everything is idempotent and self-healing: a missing invoice row or a lost PDF file is rebuilt on demand.
 * The order row lock serialises the after-commit listener and a customer's download.
 */
@Service
public class InvoiceService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<OrderStatus> INVOICEABLE = Set.of(OrderStatus.CONFIRMED, OrderStatus.PROCESSING, OrderStatus.COMPLETED);

    private final TransactionTemplate tx;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final InvoiceRepository invoices;
    private final NumberSequences sequences;
    private final FileStorage storage;
    private final InvoicePdfRenderer renderer;
    private final BusinessProperties business;

    public InvoiceService(PlatformTransactionManager txManager, OrderRepository orders, OrderItemRepository items,
                          InvoiceRepository invoices, NumberSequences sequences, FileStorage storage,
                          InvoicePdfRenderer renderer, BusinessProperties business) {
        this.tx = new TransactionTemplate(txManager);
        this.tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.orders = orders;
        this.items = items;
        this.invoices = invoices;
        this.sequences = sequences;
        this.storage = storage;
        this.renderer = renderer;
        this.business = business;
    }

    /** Creates the invoice and its PDF if they do not exist yet. Safe to call any number of times. */
    public Invoice ensureForOrder(long orderId) {
        return tx.execute(status -> ensureLocked(orderId, false));
    }

    /** The PDF of the user's own order. 404 for someone else's order, 409 while the order is not paid and confirmed. */

    /** The PDF of the user's own order. 404 for someone else's order, 409 while the order is not paid and confirmed. */
    public InvoiceFile fileForUser(long userId, String orderNumber) {
        Order order = orders.findByOrderNumberAndUserId(orderNumber, userId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "Order not found."
                ));

        return fileFor(order);
    }

    /** The PDF of any order, for staff. */
    public InvoiceFile fileForStaff(String orderNumber) {
        Order order = orders.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ORDER_NOT_FOUND",
                        "Order not found."
                ));

        return fileFor(order);
    }

    private InvoiceFile fileFor(Order order) {
        Optional<Invoice> existing = invoices.findByOrderId(order.getId())
                .filter(invoice -> invoice.getPdfStorageKey() != null);

        if (existing.isPresent()) {
            Invoice invoice = existing.get();
            Optional<byte[]> stored = storage.get(invoice.getPdfStorageKey());

            if (stored.isPresent()) {
                return new InvoiceFile(fileName(invoice), stored.get());
            }

            // The stored PDF is missing; rebuild it while holding the order lock.
            Invoice rebuilt = tx.execute(status -> ensureLocked(order.getId(), true));
            return read(rebuilt);
        }

        return read(ensureForOrder(order.getId()));
    }


    private InvoiceFile read(Invoice invoice) {
        byte[] pdf = storage.get(invoice.getPdfStorageKey()).orElseThrow(
                () -> new IllegalStateException("Invoice PDF is missing right after it was written"));
        return new InvoiceFile(fileName(invoice), pdf);
    }

    private Invoice ensureLocked(long orderId, boolean rewritePdf) {
        Order order = orders.lockById(orderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found."));
        if (order.getPaymentStatus() != PaymentStatus.PAID || !INVOICEABLE.contains(order.getOrderStatus())) {
            throw ApiException.conflict("INVOICE_NOT_AVAILABLE", "An invoice is available once the order is paid and confirmed.");
        }
        List<OrderItem> lines = items.findByOrderIdOrderByIdAsc(orderId);
        Invoice invoice = invoices.findByOrderId(orderId).orElseGet(() -> invoices.save(issue(order, lines)));

        if (invoice.getPdfStorageKey() == null || rewritePdf) {
            String key = "invoices/" + order.getPublicId() + "/" + InvoiceNumbers.storageName(invoice.getInvoiceNumber()) + ".pdf";
            storage.put(key, renderer.render(document(order, lines, invoice)));
            invoice.setPdfStorageKey(key);
            invoice = invoices.save(invoice);
        }
        return invoice;
    }

    private Invoice issue(Order order, List<OrderItem> lines) {
        Instant now = Instant.now();
        String financialYear = InvoiceNumbers.financialYear(now);
        long sequence = sequences.next(InvoiceNumbers.sequenceKey(financialYear));
        String placeOfSupply = order.getShipping().getState();
        InvoiceTax.Kind kind = InvoiceTax.kind(business.state(), placeOfSupply);

        BigDecimal cgst = new BigDecimal("0.00");
        BigDecimal sgst = new BigDecimal("0.00");
        BigDecimal igst = new BigDecimal("0.00");
        for (OrderItem line : lines) {
            InvoiceTax.Split split = InvoiceTax.split(kind, line.getTaxAmount());
            cgst = cgst.add(split.cgst());
            sgst = sgst.add(split.sgst());
            igst = igst.add(split.igst());
        }

        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(InvoiceNumbers.format(sequence, financialYear));
        invoice.setOrderId(order.getId());
        invoice.setIssuedAt(now);
        invoice.setSellerState(business.state());
        invoice.setPlaceOfSupply(placeOfSupply);
        invoice.setGstType(kind.name());
        invoice.setSubtotal(order.getSubtotal());
        invoice.setDiscountTotal(order.getDiscountTotal());
        invoice.setShippingTotal(order.getShippingTotal());
        invoice.setTaxTotal(order.getTaxTotal());
        invoice.setCgstTotal(cgst);
        invoice.setSgstTotal(sgst);
        invoice.setIgstTotal(igst);
        invoice.setGrandTotal(order.getGrandTotal());
        return invoice;
    }

    private InvoiceDocument document(Order order, List<OrderItem> lines, Invoice invoice) {
        List<InvoiceDocument.Line> printed = new ArrayList<>();
        for (OrderItem i : lines) {
            BigDecimal gross = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())).setScale(2, RoundingMode.HALF_UP);
            printed.add(new InvoiceDocument.Line(i.getProductName(),
                    "SKU " + i.getSku() + " | " + i.getSize() + " | " + i.getColor(), i.getHsnCode(), i.getQuantity(),
                    i.getUnitPrice(), i.getDiscountAmount(), gross.subtract(i.getDiscountAmount()), i.getTaxRate(),
                    i.getTaxAmount(), i.getLineTotal()));
        }
        List<String> sellerAddress = new ArrayList<>();
        add(sellerAddress, business.addressLine1());
        add(sellerAddress, business.addressLine2());
        add(sellerAddress, join(" - ", business.city(), business.pincode()));

        return new InvoiceDocument(
                new InvoiceDocument.Seller(business.name(), business.gstin(), sellerAddress, business.state(),
                        business.stateCode(), business.email(), business.phone()),
                invoice.getInvoiceNumber(), invoice.getIssuedAt().atZone(IST).toLocalDate(), order.getOrderNumber(),
                order.getPlacedAt().atZone(IST).toLocalDate(), invoice.getPlaceOfSupply(),
                InvoiceTax.Kind.valueOf(invoice.getGstType()), party(order.getBilling()), party(order.getShipping()),
                printed,
                new InvoiceDocument.Totals(invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getShippingTotal(),
                        invoice.getSubtotal().subtract(invoice.getDiscountTotal()), invoice.getCgstTotal(),
                        invoice.getSgstTotal(), invoice.getIgstTotal(), invoice.getGrandTotal()));
    }

    private static InvoiceDocument.Party party(AddressSnapshot a) {
        List<String> address = new ArrayList<>();
        add(address, a.getLine1());
        add(address, a.getLine2());
        add(address, a.getCity() + ", " + a.getState() + " - " + a.getPincode());
        return new InvoiceDocument.Party(a.getRecipientName(), a.getPhone(), address);
    }

    private static void add(List<String> lines, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(value);
        }
    }

    private static String join(String separator, String first, String second) {
        boolean a = first != null && !first.isBlank();
        boolean b = second != null && !second.isBlank();
        return a && b ? first + separator + second : a ? first : b ? second : "";
    }

    private static String fileName(Invoice invoice) {
        return InvoiceNumbers.storageName(invoice.getInvoiceNumber()) + ".pdf";
    }
}