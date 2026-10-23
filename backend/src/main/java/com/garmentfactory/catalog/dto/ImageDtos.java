package com.garmentfactory.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class ImageDtos {

    private ImageDtos() {
    }

    /**
     * {@code url} needs a staff sign-in (it works for drafts too); {@code publicUrl} is what the shop
     * uses and only works once the product is published.
     */
    public record ImageResponse(String id, String url, String publicUrl, String altText, int sortOrder,
                                boolean primary, String variantId) {
    }

    /** primary = true makes this the main image. The main image cannot be switched off directly: choose another. */
    public record ImageUpdateRequest(
            @Size(max = 200) String altText,
            @Min(0) @Max(999) Integer sortOrder,
            Boolean primary) {
    }
}