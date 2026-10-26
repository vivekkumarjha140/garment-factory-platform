package com.garmentfactory.catalog.service;

import com.garmentfactory.catalog.api.VariantTaxInfo;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VariantTaxInfoService implements VariantTaxInfo {

    private final JdbcTemplate jdbc;

    public VariantTaxInfoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<Long, String> hsnCodes(Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(variantIds.size(), "?"));
        Map<Long, String> result = new HashMap<>();
        jdbc.query("SELECT v.id, p.hsn_code FROM product_variants v JOIN products p ON p.id = v.product_id "
                + "WHERE v.id IN (" + placeholders + ")", rs -> {
            String hsn = rs.getString(2);
            if (hsn != null && !hsn.isBlank()) {
                result.put(rs.getLong(1), hsn);
            }
        }, variantIds.toArray());
        return result;
    }
}