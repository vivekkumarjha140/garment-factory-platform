package com.garmentfactory.catalog.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The catalog module's public lookup API. Inventory (and later orders) use this instead of catalog internals. */
public interface VariantLookup {

    /** Variants by internal id. Unknown ids are simply absent from the map. */
    Map<Long, VariantSummary> findByIds(Collection<Long> ids);

    Optional<VariantSummary> findById(long id);

    /** Non-archived variants whose SKU or product name contains the text (newest first, at most 100). */
    List<VariantSummary> search(String query, int limit);

    /** Keyset paging over non-archived variant ids: ids greater than afterId, ascending. */
    List<Long> variantIdsAfter(long afterId, int limit);
}