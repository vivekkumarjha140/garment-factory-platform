package com.garmentfactory.order.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * A4 tax invoice in plain Helvetica. That font has no rupee sign, so amounts say "Rs.", and characters outside
 * Latin-1 print as "?" (embed a Unicode font later if product names need it).
 */
@Component
public class InvoicePdfRenderer {

    private static final float MARGIN = 36f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float RIGHT = PAGE_WIDTH - MARGIN;
    private static final float[] COLUMNS = {18, 148, 40, 24, 52, 46, 56, 30, 46, 63};
    private static final String[] HEADINGS = {"#", "Description", "HSN", "Qty", "Rate", "Disc.", "Taxable", "GST %", "Tax", "Total"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] render(InvoiceDocument d) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Canvas c = new Canvas(pdf);
            header(c, d);
            parties(c, d);
            table(c, d);
            totals(c, d);
            c.y -= 24;
            c.ensure(20);
            c.text("This is a computer generated invoice and does not require a signature.", MARGIN, c.y, 7, false);
            c.close();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void header(Canvas c, InvoiceDocument d) throws IOException {
        c.text("TAX INVOICE", MARGIN, c.y, 16, true);
        c.y -= 22;
        float top = c.y;
        InvoiceDocument.Seller s = d.seller();
        c.text(s.name(), MARGIN, c.y, 11, true);
        c.y -= 12;
        for (String line : s.addressLines()) {
            c.text(line, MARGIN, c.y, 8, false);
            c.y -= 10;
        }
        c.text("GSTIN: " + blankAs(s.gstin(), "not set"), MARGIN, c.y, 8, true);
        c.y -= 10;
        c.text("State: " + s.state() + " (" + s.stateCode() + ")", MARGIN, c.y, 8, false);
        c.y -= 10;
        c.text(join(" | ", s.email(), s.phone()), MARGIN, c.y, 8, false);
        float leftEnd = c.y;

        float y = top;
        String[][] facts = {
                {"Invoice No:", d.invoiceNumber()},
                {"Invoice Date:", d.issuedOn().format(DATE)},
                {"Order No:", d.orderNumber()},
                {"Order Date:", d.orderDate().format(DATE)},
                {"Place of Supply:", d.placeOfSupply()},
                {"Payment:", "Paid"}};
        for (String[] fact : facts) {
            c.text(fact[0], 330, y, 8, true);
            c.text(fact[1], 410, y, 8, false);
            y -= 11;
        }
        c.y = Math.min(leftEnd, y) - 14;
    }

    private void parties(Canvas c, InvoiceDocument d) throws IOException {
        c.ensure(80);
        float top = c.y;
        float left = party(c, "Bill To", d.billTo(), MARGIN, top);
        float right = party(c, "Ship To", d.shipTo(), 330, top);
        c.y = Math.min(left, right) - 10;
    }

    private float party(Canvas c, String title, InvoiceDocument.Party p, float x, float top) throws IOException {
        float y = top;
        c.text(title, x, y, 9, true);
        y -= 11;
        c.text(p.name(), x, y, 8, false);
        y -= 10;
        for (String line : p.addressLines()) {
            c.text(line, x, y, 8, false);
            y -= 10;
        }
        c.text("Phone: " + p.phone(), x, y, 8, false);
        return y - 10;
    }

    private void table(Canvas c, InvoiceDocument d) throws IOException {
        tableHeading(c);
        int number = 1;
        for (InvoiceDocument.Line line : d.lines()) {
            if (c.y - 26 < MARGIN + 150) {
                c.newPage();
                tableHeading(c);
            }
            float x = MARGIN;
            float y = c.y;
            String[] cells = {String.valueOf(number++), null, blankAs(line.hsn(), "-"), String.valueOf(line.quantity()),
                    money(line.rate()), money(line.discount()), money(line.taxable()), line.gstRate().stripTrailingZeros().toPlainString(),
                    money(line.tax()), money(line.total())};
            for (int i = 0; i < COLUMNS.length; i++) {
                if (i == 1) {
                    c.text(c.fit(line.description(), COLUMNS[1] - 4, 7.5f, false), x, y, 7.5f, false);
                    c.text(c.fit(line.detail(), COLUMNS[1] - 4, 6.5f, false), x, y - 9, 6.5f, false);
                } else if (i == 0 || i == 2) {
                    c.text(cells[i], x, y, 7.5f, false);
                } else {
                    c.textRight(cells[i], x + COLUMNS[i] - 3, y, 7.5f, false);
                }
                x += COLUMNS[i];
            }
            c.y -= 24;
        }
        c.line(MARGIN, RIGHT, c.y + 10);
        c.y -= 6;
    }

    private void tableHeading(Canvas c) throws IOException {
        c.line(MARGIN, RIGHT, c.y + 10);
        float x = MARGIN;
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i <= 2) {
                c.text(HEADINGS[i], x, c.y, 7.5f, true);
            } else {
                c.textRight(HEADINGS[i], x + COLUMNS[i] - 3, c.y, 7.5f, true);
            }
            x += COLUMNS[i];
        }
        c.line(MARGIN, RIGHT, c.y - 4);
        c.y -= 16;
    }

    private void totals(Canvas c, InvoiceDocument d) throws IOException {
        InvoiceDocument.Totals t = d.totals();
        c.ensure(130);
        total(c, "Subtotal", t.subtotal(), false);
        total(c, "Discount", t.discount().negate(), false);
        total(c, "Taxable value", t.taxable(), false);
        if (d.taxKind() == InvoiceTax.Kind.INTRA_STATE) {
            total(c, "CGST", t.cgst(), false);
            total(c, "SGST", t.sgst(), false);
        } else {
            total(c, "IGST", t.igst(), false);
        }
        total(c, "Shipping (no GST charged)", t.shipping(), false);
        c.line(340, RIGHT, c.y + 9);
        total(c, "Grand Total", t.grandTotal(), true);
    }

    private void total(Canvas c, String label, BigDecimal value, boolean bold) throws IOException {
        c.text(label, 340, c.y, 9, bold);
        c.textRight("Rs. " + money(value), RIGHT, c.y, 9, bold);
        c.y -= 13;
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String blankAs(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String join(String separator, String... parts) {
        return String.join(separator, List.of(parts).stream().filter(p -> p != null && !p.isBlank()).toList());
    }

    /** Cursor-based drawing helper that starts a new page when the current one is full. */
    private static final class Canvas implements AutoCloseable {
        private final PDDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        float y;

        Canvas(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        void ensure(float height) throws IOException {
            if (y - height < MARGIN) {
                newPage();
            }
        }

        void text(String value, float x, float yy, float size, boolean isBold) throws IOException {
            stream.beginText();
            stream.setFont(isBold ? bold : regular, size);
            stream.newLineAtOffset(x, yy);
            stream.showText(clean(value));
            stream.endText();
        }

        void textRight(String value, float rightX, float yy, float size, boolean isBold) throws IOException {
            text(value, rightX - width(value, size, isBold), yy, size, isBold);
        }

        void line(float x1, float x2, float yy) throws IOException {
            stream.setLineWidth(0.5f);
            stream.moveTo(x1, yy);
            stream.lineTo(x2, yy);
            stream.stroke();
        }

        float width(String value, float size, boolean isBold) throws IOException {
            return (isBold ? bold : regular).getStringWidth(clean(value)) / 1000f * size;
        }

        String fit(String value, float maxWidth, float size, boolean isBold) throws IOException {
            String text = clean(value);
            if (width(text, size, isBold) <= maxWidth) {
                return text;
            }
            while (text.length() > 1 && width(text + "...", size, isBold) > maxWidth) {
                text = text.substring(0, text.length() - 1);
            }
            return text + "...";
        }

        private static String clean(String value) {
            return value == null ? "" : value.replaceAll("[^\\x20-\\x7E\\u00A0-\\u00FF]", "?");
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}