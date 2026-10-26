package com.garmentfactory.catalog.service;

import com.garmentfactory.catalog.api.VariantPricing;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VariantPricingService implements VariantPricing {

    /** Status value (variant AND product) that means "can be bought". */
    private static final String SELLABLE_STATUS = "ACTIVE";

    private record VariantRow(long id, String publicId, long productId, String productPublicId, String productName,
                              String slug, String sku, String size, String colorName, BigDecimal priceOverride,
                              BigDecimal basePrice, BigDecimal gstRate, Integer weightG, boolean sellable) {
    }

    private record TierRow(long productId, PriceTiers.Tier tier) {
    }

    private static final RowMapper<VariantRow> VARIANT_ROW = (rs, n) -> new VariantRow(
            rs.getLong("id"),
            rs.getString("public_id"),
            rs.getLong("product_id"),
            rs.getString("product_public_id"),
            rs.getString("product_name"),
            rs.getString("slug"),
            rs.getString("sku"),
            rs.getString("size"),
            rs.getString("color_name"),
            rs.getBigDecimal("price_override"),
            rs.getBigDecimal("base_price"),
            rs.getBigDecimal("gst_rate"),
            rs.getObject("weight_g", Integer.class),
            SELLABLE_STATUS.equals(rs.getString("variant_status"))
                    && SELLABLE_STATUS.equals(rs.getString("product_status")));

    private static final RowMapper<TierRow> TIER_ROW = (rs, n) -> new TierRow(
            rs.getLong("product_id"),
            new PriceTiers.Tier(rs.getInt("min_qty"), rs.getObject("max_qty", Integer.class),
                    rs.getBigDecimal("unit_price")));

    private final JdbcClient jdbc;

    public VariantPricingService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public Optional<Long> resolveVariantId(String variantPublicId) {
        if (variantPublicId == null || variantPublicId.isBlank()) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM product_variants WHERE public_id = :publicId")
                .param("publicId", variantPublicId.trim())
                .query(Long.class)
                .optional();
    }

    @Override
    public Map<Long, PricedVariant> priceLines(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }

        List<Long> ids = lines.stream().map(Line::variantId).distinct().toList();
        Map<Long, VariantRow> rows = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT v.id, v.public_id, v.product_id, v.sku, v.size, v.color_name,
                               v.price_override, v.weight_g, v.status AS variant_status,
                               p.public_id AS product_public_id, p.name AS product_name, p.slug,
                               p.base_price, p.gst_rate, p.status AS product_status
                          FROM product_variants v
                          JOIN products p ON p.id = v.product_id
                         WHERE v.id IN (:ids)
                        """)
                .param("ids", ids)
                .query(VARIANT_ROW)
                .list()
                .forEach(row -> rows.put(row.id(), row));
        if (rows.isEmpty()) {
            return Map.of();
        }

        Map<Long, Integer> quantityByProduct = new HashMap<>();
        for (Line line : lines) {
            VariantRow row = rows.get(line.variantId());
            if (row != null) {
                quantityByProduct.merge(row.productId(), line.quantity(), Integer::sum);
            }
        }
        Map<Long, List<PriceTiers.Tier>> tiers = loadTiers(quantityByProduct.keySet());

        Map<Long, PricedVariant> result = new LinkedHashMap<>();
        for (Line line : lines) {
            VariantRow row = rows.get(line.variantId());
            if (row == null) {
                continue;
            }
            // A variant-level price override is a fixed price for that variant: it wins over tiers.
            BigDecimal unit = row.priceOverride() != null
                    ? row.priceOverride()
                    : TierPricing.unitPrice(row.basePrice(), tiers.getOrDefault(row.productId(), List.of()),
                    quantityByProduct.get(row.productId()));

            result.put(row.id(), new PricedVariant(row.id(), row.publicId(), row.productPublicId(),
                    row.productName(), row.slug(), row.sku(), row.size(), row.colorName(),
                    unit.setScale(2, RoundingMode.HALF_UP), row.gstRate(), row.weightG(), row.sellable()));
        }
        return result;
    }

    private Map<Long, List<PriceTiers.Tier>> loadTiers(Collection<Long> productIds) {
        Map<Long, List<PriceTiers.Tier>> byProduct = new HashMap<>();
        jdbc.sql("""
                        SELECT product_id, min_qty, max_qty, unit_price
                          FROM quantity_price_tiers
                         WHERE product_id IN (:ids)
                         ORDER BY product_id, min_qty
                        """)
                .param("ids", List.copyOf(productIds))
                .query(TIER_ROW)
                .list()
                .forEach(t -> byProduct.computeIfAbsent(t.productId(), k -> new ArrayList<>()).add(t.tier()));
        return byProduct;
    }
}