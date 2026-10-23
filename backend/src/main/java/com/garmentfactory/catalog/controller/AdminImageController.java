package com.garmentfactory.catalog.controller;

import com.garmentfactory.catalog.dto.ImageDtos.ImageResponse;
import com.garmentfactory.catalog.dto.ImageDtos.ImageUpdateRequest;
import com.garmentfactory.catalog.service.ImageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Staff management of product images. Reading needs PRODUCT_VIEW, changing needs PRODUCT_WRITE. */
@RestController
@RequestMapping("/api/v1/admin/catalog")
@Tag(name = "Admin: product images")
public class AdminImageController {

    private final ImageService images;

    public AdminImageController(ImageService images) {
        this.images = images;
    }

    @GetMapping("/products/{id}/images")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public List<ImageResponse> list(@PathVariable String id) {
        return images.list(id);
    }

    /** Multipart form: file (required), altText and variantId (optional). JPEG or PNG, up to 5 MB. */
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ImageResponse upload(@PathVariable String id,
                                @RequestParam("file") MultipartFile file,
                                @RequestParam(required = false) String altText,
                                @RequestParam(required = false) String variantId) {
        return images.upload(id, file, altText, variantId);
    }

    @PutMapping("/products/{id}/images/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public ImageResponse update(@PathVariable String id, @PathVariable String imageId,
                                @Valid @RequestBody ImageUpdateRequest request) {
        return images.update(id, imageId, request);
    }

    @DeleteMapping("/products/{id}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PRODUCT_WRITE')")
    public void delete(@PathVariable String id, @PathVariable String imageId) {
        images.delete(id, imageId);
    }

    /** Staff preview of any image, including those of drafts. */
    @GetMapping("/images/{imageId}")
    @PreAuthorize("hasAuthority('PRODUCT_VIEW')")
    public ResponseEntity<byte[]> view(@PathVariable String imageId) {
        ImageService.LoadedImage image = images.load(imageId, false);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}