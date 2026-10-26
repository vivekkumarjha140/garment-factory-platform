package com.garmentfactory.order.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything printed on an invoice. The renderer needs nothing else, so it is testable without a database. */
public record InvoiceDocument(Seller seller, String invoiceNumber, LocalDate issuedOn, String orderNumber,
                              LocalDate orderDate, String placeOfSupply, InvoiceTax.Kind taxKind, Party billTo,
                              Party shipTo, List<Line> lines, Totals totals) {

    public record Seller(String name, String gstin, List<String> addressLines, String state, String stateCode,
                         String email, String phone) {
    }

    public record Party(String name, String phone, List<String> addressLines) {
    }

    public record Line(String description, String detail, String hsn, int quantity, BigDecimal rate,
                       BigDecimal discount, BigDecimal taxable, BigDecimal gstRate, BigDecimal tax, BigDecimal total) {
    }

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal taxable,
                         BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal grandTotal) {
    }
}