package com.garmentfactory.catalog.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.catalog.dto.ImageDtos.ImageResponse;
import com.garmentfactory.catalog.dto.ImageDtos.ImageUpdateRequest;
import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductImage;
import com.garmentfactory.catalog.entity.ProductStatus;
import com.garmentfactory.catalog.entity.ProductVariant;
import com.garmentfactory.catalog.repository.ProductImageRepository;
import com.garmentfactory.catalog.repository.ProductRepository;
import com.garmentfactory.catalog.repository.ProductVariantRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.util.AfterCommit;
import com.garmentfactory.storage.api.FileStorage;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Product images. Every change locks the product row first, so two uploads at the same moment can
 * neither exceed the limit nor both become the primary image. Files get server-generated keys; the
 * client's file name is never used.
 */
@Service
public class ImageService {

    public static final int MAX_IMAGES_PER_PRODUCT = 10;
    private static final int MAX_ALT_TEXT = 200;

    public record LoadedImage(byte[] bytes, String contentType) {
    }

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final FileStorage storage;
    private final AuditRecorder audit;

    public ImageService(ProductRepository products, ProductVariantRepository variants,
                        ProductImageRepository images, FileStorage storage, AuditRecorder audit) {
        this.products = products;
        this.variants = variants;
        this.images = images;
        this.storage = storage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> list(String productPublicId) {
        Product product = products.findByPublicId(productPublicId).orElseThrow(ImageService::productNotFound);
        return images.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream().map(ImageService::toResponse).toList();
    }

    @Transactional
    public ImageResponse upload(String productPublicId, MultipartFile file, String altText, String variantPublicId) {
        Product product = products.lockByPublicId(productPublicId).orElseThrow(ImageService::productNotFound);
        if (product.getStatus() == ProductStatus.ARCHIVED) {
            throw ApiException.conflict("PRODUCT_ARCHIVED", "An archived product cannot be changed. Restore it to draft first.");
        }
        List<ProductImage> existing = images.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        if (existing.size() >= MAX_IMAGES_PER_PRODUCT) {
            throw ApiException.conflict("IMAGE_LIMIT_REACHED", "A product can have at most " + MAX_IMAGES_PER_PRODUCT + " images.");
        }
        ProductVariant variant = null;
        if (variantPublicId != null && !variantPublicId.isBlank()) {
            variant = variants.findByPublicIdAndProductId(variantPublicId, product.getId())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_VARIANT", "The variant does not belong to this product."));
        }
        String alt = cleanAltText(altText);

        byte[] bytes = readBytes(file);
        ImageValidator.ValidatedImage validated = ImageValidator.validate(bytes);

        String imageId = UUID.randomUUID().toString();
        String key = "products/" + product.getPublicId() + "/" + imageId + validated.extension();
        storage.put(key, bytes);

        ProductImage image = new ProductImage();
        image.setPublicId(imageId);
        image.setProduct(product);
        image.setVariant(variant);
        image.setObjectKey(key);
        image.setAltText(alt);
        image.setPrimaryImage(existing.isEmpty());
        image.setSortOrder(existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1);
        try {
            images.save(image);
        } catch (RuntimeException ex) {
            storage.delete(key);
            throw ex;
        }

        audit.record(AuditEvent.of("PRODUCT_IMAGE_ADDED", "PRODUCT", product.getPublicId(),
                "image=" + imageId + ", " + validated.width() + "x" + validated.height() + ", " + bytes.length + " bytes"));
        return toResponse(image);
    }

    @Transactional
    public ImageResponse update(String productPublicId, String imagePublicId, ImageUpdateRequest request) {
        Product product = products.lockByPublicId(productPublicId).orElseThrow(ImageService::productNotFound);
        ProductImage image = findImage(product, imagePublicId);

        if (request.altText() != null) {
            image.setAltText(cleanAltText(request.altText()));
        }
        if (request.sortOrder() != null) {
            image.setSortOrder(request.sortOrder());
        }
        if (Boolean.TRUE.equals(request.primary()) && !image.isPrimaryImage()) {
            images.clearPrimary(product.getId());
            image.setPrimaryImage(true);
        }
        audit.record(AuditEvent.of("PRODUCT_IMAGE_UPDATED", "PRODUCT", product.getPublicId(), "image=" + image.getPublicId()));
        return toResponse(image);
    }

    /** Deleting the main image promotes the next one in order, so a product with images always has a main image. */
    @Transactional
    public void delete(String productPublicId, String imagePublicId) {
        Product product = products.lockByPublicId(productPublicId).orElseThrow(ImageService::productNotFound);
        ProductImage image = findImage(product, imagePublicId);
        boolean wasPrimary = image.isPrimaryImage();
        String key = image.getObjectKey();

        images.delete(image);
        images.flush();
        if (wasPrimary) {
            images.findByProductIdOrderBySortOrderAscIdAsc(product.getId()).stream().findFirst()
                    .ifPresent(next -> next.setPrimaryImage(true));
        }
        audit.record(AuditEvent.of("PRODUCT_IMAGE_REMOVED", "PRODUCT", product.getPublicId(), "image=" + imagePublicId));
        AfterCommit.run(() -> storage.delete(key));
    }

    /** Public requests may only load images of published products; staff may load any. */
    @Transactional(readOnly = true)
    public LoadedImage load(String imagePublicId, boolean publicOnly) {
        ProductImage image = images.findWithProductByPublicId(imagePublicId).orElseThrow(ImageService::imageNotFound);
        if (publicOnly) {
            Product product = image.getProduct();
            if (product.getStatus() != ProductStatus.ACTIVE || !product.getCategory().isActive()) {
                throw imageNotFound();
            }
        }
        byte[] bytes = storage.get(image.getObjectKey()).orElseThrow(ImageService::imageNotFound);
        return new LoadedImage(bytes, image.getObjectKey().endsWith(".png") ? "image/png" : "image/jpeg");
    }

    private ProductImage findImage(Product product, String imagePublicId) {
        return images.findByPublicIdAndProductId(imagePublicId, product.getId()).orElseThrow(ImageService::imageNotFound);
    }

    private static byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("INVALID_IMAGE", "The file is empty.");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw ApiException.badRequest("INVALID_IMAGE", "The file could not be read.");
        }
    }

    private static String cleanAltText(String altText) {
        if (altText == null || altText.isBlank()) {
            return null;
        }
        String trimmed = altText.trim();
        if (trimmed.length() > MAX_ALT_TEXT) {
            throw ApiException.badRequest("INVALID_ALT_TEXT", "Alt text can be at most " + MAX_ALT_TEXT + " characters.");
        }
        return trimmed;
    }

    private static ImageResponse toResponse(ProductImage image) {
        return new ImageResponse(image.getPublicId(),
                "/api/v1/admin/catalog/images/" + image.getPublicId(),
                "/api/v1/catalog/images/" + image.getPublicId(),
                image.getAltText(), image.getSortOrder(), image.isPrimaryImage(),
                image.getVariant() == null ? null : image.getVariant().getPublicId());
    }

    private static ApiException productNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found.");
    }

    private static ApiException imageNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Image not found.");
    }
}