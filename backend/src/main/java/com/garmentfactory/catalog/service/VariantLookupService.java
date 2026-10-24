
package com.garmentfactory.catalog.service;

import com.garmentfactory.catalog.api.VariantLookup;
import com.garmentfactory.catalog.api.VariantSummary;
import java.util.Collection;
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
public class VariantLookupService implements VariantLookup {

    private static final int MAX_LIMIT = 100;

    private static final String SELECT = """
            SELECT v.id, v.public_id, v.sku, v.size, v.color_name, v.status,
                   p.public_id AS product_public_id, p.name AS product_name
              FROM product_variants v
              JOIN products p ON p.id = v.product_id
            """;

    private static final RowMapper<VariantSummary> MAPPER = (rs, row) ->
            new VariantSummary(
                    rs.getLong("id"),
                    rs.getString("public_id"),
                    rs.getString("sku"),
                    rs.getString("size"),
                    rs.getString("color_name"),
                    rs.getString("status"),
                    rs.getString("product_public_id"),
                    rs.getString("product_name")
            );

    private final JdbcClient jdbc;

    public VariantLookupService(JdbcTemplate jdbcTemplate) {
        this.jdbc = JdbcClient.create(jdbcTemplate);
    }

    @Override
    public Map<Long, VariantSummary> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, VariantSummary> result = new LinkedHashMap<>();

        jdbc.sql(SELECT + " WHERE v.id IN (:ids) ORDER BY v.id")
                .param("ids", ids)
                .query(MAPPER)
                .list()
                .forEach(variant -> result.put(variant.id(), variant));

        return result;
    }

    @Override
    public Optional<VariantSummary> findById(long id) {
        return jdbc.sql(SELECT + " WHERE v.id = :id")
                .param("id", id)
                .query(MAPPER)
                .optional();
    }

    @Override
    public List<VariantSummary> search(String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), MAX_LIMIT);

        if (query == null || query.isBlank()) {
            return jdbc.sql(
                            SELECT
                                    + " WHERE v.status <> 'ARCHIVED'"
                                    + " ORDER BY v.id DESC LIMIT :limit"
                    )
                    .param("limit", capped)
                    .query(MAPPER)
                    .list();
        }

        String pattern = "%" + escapeLike(query.trim()) + "%";

        return jdbc.sql(
                        SELECT
                                + " WHERE v.status <> 'ARCHIVED'"
                                + " AND (v.sku LIKE :q OR p.name LIKE :q)"
                                + " ORDER BY v.id DESC LIMIT :limit"
                )
                .param("q", pattern)
                .param("limit", capped)
                .query(MAPPER)
                .list();
    }

    @Override
    public List<Long> variantIdsAfter(long afterId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 1000);

        return jdbc.sql("""
                        SELECT id
                          FROM product_variants
                         WHERE id > :after
                           AND status <> 'ARCHIVED'
                         ORDER BY id
                         LIMIT :limit
                        """)
                .param("after", afterId)
                .param("limit", capped)
                .query(Long.class)
                .list();
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
