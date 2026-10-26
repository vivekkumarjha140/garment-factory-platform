package com.garmentfactory.cart.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * GST per line at each product's own rate. A coupon discount is spread over the lines in proportion
 * to their value (last line takes the rounding remainder), and GST is charged on the discounted value.
 * Prices are treated as tax-EXCLUSIVE. Shipping charge carries no GST here.
 */
@Component
public class TaxCalculator {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public record TaxableLine(BigDecimal lineTotal, BigDecimal gstRate) {
    }

    /** taxable and gst are in the same order as the input lines. */
    public record Result(List<BigDecimal> taxable, List<BigDecimal> gst, BigDecimal totalTaxable, BigDecimal totalGst) {
    }

    public Result compute(List<TaxableLine> lines, BigDecimal discount) {
        BigDecimal subtotal = lines.stream().map(TaxableLine::lineTotal).reduce(ZERO, BigDecimal::add);
        BigDecimal totalDiscount = discount.max(BigDecimal.ZERO).min(subtotal);

        List<BigDecimal> taxable = new ArrayList<>();
        List<BigDecimal> gst = new ArrayList<>();
        BigDecimal allocated = ZERO;
        BigDecimal totalTaxable = ZERO;
        BigDecimal totalGst = ZERO;

        for (int i = 0; i < lines.size(); i++) {
            TaxableLine line = lines.get(i);
            BigDecimal share;
            if (i == lines.size() - 1) {
                share = totalDiscount.subtract(allocated);
            } else if (subtotal.signum() == 0) {
                share = ZERO;
            } else {
                share = totalDiscount.multiply(line.lineTotal()).divide(subtotal, 2, RoundingMode.HALF_UP);
            }
            share = share.max(BigDecimal.ZERO).min(line.lineTotal());
            allocated = allocated.add(share);

            BigDecimal lineTaxable = line.lineTotal().subtract(share).setScale(2, RoundingMode.HALF_UP);
            BigDecimal lineGst = lineTaxable.multiply(line.gstRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);

            taxable.add(lineTaxable);
            gst.add(lineGst);
            totalTaxable = totalTaxable.add(lineTaxable);
            totalGst = totalGst.add(lineGst);
        }
        return new Result(taxable, gst, totalTaxable, totalGst);
    }
}