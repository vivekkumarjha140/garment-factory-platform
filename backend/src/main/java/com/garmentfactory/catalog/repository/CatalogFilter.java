package com.garmentfactory.catalog.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Everything a shopper can filter by. A null field means "no filter". Sizes and colours are matched
 * on one and the same variant: "size M and colour Blue" needs a Blue variant in size M.
 */
public record CatalogFilter(
        String pattern,
        Set<Long> categoryIds,
        Long brandId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> sizes,
        List<String> colors,
        Boolean customizable,
        CatalogSort sort) {
}