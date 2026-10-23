package com.garmentfactory.shared.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable JSON shape for paged lists (Spring's own Page serialization is not a stable contract). */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}