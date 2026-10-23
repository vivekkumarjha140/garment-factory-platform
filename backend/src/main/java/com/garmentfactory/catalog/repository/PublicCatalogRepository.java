package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.VariantStatus;
import com.garmentfactory.catalog.service.Sizes;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Read queries of the public shop. The filter list is dynamic, so the JPQL is assembled from fixed
 * fragments and every value is bound as a parameter (nothing from the request is ever concatenated).
 * A product is visible only if it is published, its category is active, its brand (if any) is active
 * and it has at least one active variant.
 */
@Repository
public class PublicCatalogRepository {

    public record Color(String name, String hex) {
    }

    public record Facets(List<String> sizes, List<Color> colors, BigDecimal minPrice, BigDecimal maxPrice) {
    }

    /** Lowest price a shopper can pay: the cheapest active variant (its own price, or the product's). */
    private static final String FROM_PRICE = "(select min(coalesce(pv.priceOverride, p.basePrice)) "
            + "from ProductVariant pv where pv.product = p and pv.status = :variantActive)";

    private static final String VISIBLE = " p.status = :productActive and c.active = true and (b is null or b.active = true) "
            + "and exists (select 1 from ProductVariant av where av.product = p and av.status = :variantActive)";

    @PersistenceContext
    private EntityManager em;

    public Page<Product> search(CatalogFilter filter, Pageable pageable) {
        Map<String, Object> params = new HashMap<>();
        params.put("productActive", ProductStatus.ACTIVE);
        params.put("variantActive", VariantStatus.ACTIVE);
        StringBuilder where = new StringBuilder(" where").append(VISIBLE);

        if (filter.categoryIds() != null) {
            where.append(" and c.id in :categoryIds");
            params.put("categoryIds", filter.categoryIds());
        }
        if (filter.pattern() != null) {
            where.append(" and (lower(p.name) like :pattern or lower(p.slug) like :pattern "
                    + "or lower(p.description) like :pattern or lower(b.name) like :pattern)");
            params.put("pattern", filter.pattern());
        }
        if (filter.brandId() != null) {
            where.append(" and b.id = :brandId");
            params.put("brandId", filter.brandId());
        }
        if (filter.minPrice() != null) {
            where.append(" and ").append(FROM_PRICE).append(" >= :minPrice");
            params.put("minPrice", filter.minPrice());
        }
        if (filter.maxPrice() != null) {
            where.append(" and ").append(FROM_PRICE).append(" <= :maxPrice");
            params.put("maxPrice", filter.maxPrice());
        }
        boolean hasSizes = filter.sizes() != null && !filter.sizes().isEmpty();
        boolean hasColors = filter.colors() != null && !filter.colors().isEmpty();
        if (hasSizes || hasColors) {
            where.append(" and exists (select 1 from ProductVariant fv where fv.product = p and fv.status = :variantActive");
            if (hasSizes) {
                where.append(" and fv.size in :sizes");
                params.put("sizes", filter.sizes());
            }
            if (hasColors) {
                where.append(" and fv.colorName in :colors");
                params.put("colors", filter.colors());
            }
            where.append(")");
        }
        if (filter.customizable() != null) {
            where.append(" and p.customizable = :customizable");
            params.put("customizable", filter.customizable());
        }

        TypedQuery<Long> count = em.createQuery(
                "select count(p) from Product p join p.category c left join p.brand b" + where, Long.class);
        bind(count, params);
        long total = count.getSingleResult();
        if (total == 0 || pageable.getOffset() >= total) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        TypedQuery<Product> query = em.createQuery(
                "select p from Product p join fetch p.category c left join fetch p.brand b" + where + orderBy(filter.sort()),
                Product.class);
        bind(query, params);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());
        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    /** The "from" price of each product, for a whole page in one query. */
    public Map<Long, BigDecimal> fromPrices(Collection<Long> productIds) {
        Map<Long, BigDecimal> prices = new HashMap<>();
        if (productIds.isEmpty()) {
            return prices;
        }
        List<Object[]> rows = em.createQuery("select v.product.id, min(coalesce(v.priceOverride, v.product.basePrice)) "
                        + "from ProductVariant v where v.product.id in :ids and v.status = :active group by v.product.id", Object[].class)
                .setParameter("ids", productIds)
                .setParameter("active", VariantStatus.ACTIVE)
                .getResultList();
        for (Object[] row : rows) {
            prices.put((Long) row[0], (BigDecimal) row[1]);
        }
        return prices;
    }

    /** Public id of each product's primary image, for a whole page in one query. */
    public Map<Long, String> primaryImageIds(Collection<Long> productIds) {
        Map<Long, String> images = new HashMap<>();
        if (productIds.isEmpty()) {
            return images;
        }
        List<Object[]> rows = em.createQuery("select i.product.id, i.publicId from ProductImage i "
                        + "where i.product.id in :ids and i.primaryImage = true", Object[].class)
                .setParameter("ids", productIds)
                .getResultList();
        for (Object[] row : rows) {
            images.put((Long) row[0], (String) row[1]);
        }
        return images;
    }

    /** The sizes, colours and price range available among visible products (optionally within some categories). */
    public Facets facets(Set<Long> categoryIds) {
        String base = " from ProductVariant v join v.product p join p.category c left join p.brand b "
                + "where p.status = :productActive and v.status = :variantActive and c.active = true "
                + "and (b is null or b.active = true)" + (categoryIds != null ? " and c.id in :categoryIds" : "");

        List<String> sizes = bindFacet(em.createQuery("select distinct v.size" + base, String.class), categoryIds).getResultList();
        sizes.sort(Comparator.comparingInt((String size) -> {
            int index = Sizes.all().indexOf(size);
            return index < 0 ? Integer.MAX_VALUE : index;
        }).thenComparing(Comparator.naturalOrder()));

        List<Object[]> colorRows = bindFacet(em.createQuery("select distinct v.colorName, v.colorHex" + base, Object[].class), categoryIds)
                .getResultList();
        List<Color> colors = colorRows.stream()
                .map(row -> new Color((String) row[0], (String) row[1]))
                .sorted(Comparator.comparing(Color::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Object[] range = bindFacet(em.createQuery("select min(coalesce(v.priceOverride, p.basePrice)), "
                + "max(coalesce(v.priceOverride, p.basePrice))" + base, Object[].class), categoryIds).getSingleResult();
        return new Facets(sizes, colors, (BigDecimal) range[0], (BigDecimal) range[1]);
    }

    private static <T> TypedQuery<T> bindFacet(TypedQuery<T> query, Set<Long> categoryIds) {
        query.setParameter("productActive", ProductStatus.ACTIVE);
        query.setParameter("variantActive", VariantStatus.ACTIVE);
        if (categoryIds != null) {
            query.setParameter("categoryIds", categoryIds);
        }
        return query;
    }

    private static void bind(TypedQuery<?> query, Map<String, Object> params) {
        params.forEach(query::setParameter);
    }

    private static String orderBy(CatalogSort sort) {
        return switch (sort) {
            case NEWEST -> " order by p.createdAt desc, p.id desc";
            case PRICE_ASC -> " order by " + FROM_PRICE + " asc, p.id asc";
            case PRICE_DESC -> " order by " + FROM_PRICE + " desc, p.id desc";
            case NAME -> " order by p.name asc, p.id asc";
            case RATING -> " order by p.avgRating desc, p.reviewCount desc, p.id desc";
        };
    }
}