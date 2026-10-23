package com.garmentfactory.shared.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public final class Paging {

    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /** Clamps client input: page at least 0, size between 1 and 100. Sorting is fixed by each query. */
    public static Pageable of(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE));
    }
}