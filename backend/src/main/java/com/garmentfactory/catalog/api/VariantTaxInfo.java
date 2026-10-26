package com.garmentfactory.catalog.api;

import java.util.Collection;
import java.util.Map;

/** Tax facts of variants, for freezing into order lines. */
public interface VariantTaxInfo {

    /** Variants whose product has no HSN code are absent from the map. */
    Map<Long, String> hsnCodes(Collection<Long> variantIds);
}