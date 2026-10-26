package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.order.service.InvoiceDocument;
import com.garmentfactory.order.service.InvoicePdfRenderer;
import com.garmentfactory.order.service.InvoiceTax;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    private static InvoiceDocument document(InvoiceTax.Kind kind, List<InvoiceDocument.Line> lines) {
        InvoiceDocument.Party party = new InvoiceDocument.Party("Test User", "9876543210", List.of("12 MG Road", "Gurugram, Haryana - 122001"));
        return new InvoiceDocument(
                new InvoiceDocument.Seller("Test Garments Pvt Ltd", "06ABCDE1234F1Z5", List.of("Plot 1, Industrial Area"), "Haryana", "06", "billing@example.com", "0123456789"),
                "GF/26-27/000001", LocalDate.of(2026, 10, 26), "GF-2026-000001", LocalDate.of(2026, 10, 26),
                "Haryana", kind, party, party, lines,
                new InvoiceDocument.Totals(new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("50.00"),
                        new BigDecimal("900.00"), new BigDecimal("22.50"), new BigDecimal("22.50"), new BigDecimal("0.00"), new BigDecimal("995.00")));
    }

    private static InvoiceDocument.Line line(String name) {
        return new InvoiceDocument.Line(name, "SKU TEE-1 | M | Red", "6109", 2, new BigDecimal("500.00"),
                new BigDecimal("100.00"), new BigDecimal("900.00"), new BigDecimal("5.00"), new BigDecimal("45.00"), new BigDecimal("945.00"));
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void anIntraStateInvoiceShowsTheLegalDetailsAndCgstSgst() throws Exception {
        String text = textOf(renderer.render(document(InvoiceTax.Kind.INTRA_STATE, List.of(line("Classic Tee")))));

        assertThat(text).contains("TAX INVOICE", "GF/26-27/000001", "GF-2026-000001", "06ABCDE1234F1Z5",
                "Test Garments Pvt Ltd", "6109", "CGST", "SGST", "Rs. 995.00");
        assertThat(text).doesNotContain("IGST");
    }

    @Test
    void anInterStateInvoiceShowsIgstOnly() throws Exception {
        String text = textOf(renderer.render(document(InvoiceTax.Kind.INTER_STATE, List.of(line("Classic Tee")))));

        assertThat(text).contains("IGST").doesNotContain("CGST");
    }

    @Test
    void manyLinesFlowOntoFurtherPages() throws Exception {
        List<InvoiceDocument.Line> lines = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            lines.add(line("Item number " + i));
        }
        byte[] pdf = renderer.render(document(InvoiceTax.Kind.INTRA_STATE, lines));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        assertThat(textOf(pdf)).contains("Item number 60", "Grand Total");
    }

    @Test
    void textOutsideLatinOneIsReplacedInsteadOfBreakingTheInvoice() throws Exception {
        byte[] pdf = renderer.render(document(InvoiceTax.Kind.INTRA_STATE, List.of(line("टी-शर्ट Tee"))));

        assertThat(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        assertThat(textOf(pdf)).contains("Tee");
    }
}