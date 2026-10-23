package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.ProductVariant;
import com.garmentfactory.catalog.entity.VariantStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductIdOrderByCreatedAtAscIdAsc(Long productId);

    List<ProductVariant> findByProductIdAndStatusOrderByCreatedAtAscIdAsc(Long productId, VariantStatus status);

    Optional<ProductVariant> findByPublicIdAndProductId(String publicId, Long productId);

    long countByProductId(Long productId);

    long countByProductIdAndStatus(Long productId, VariantStatus status);

    // The "...IdNot" forms serve both create (pass 0, no row has that id) and update (pass the row's own id).
    boolean existsBySkuAndIdNot(String sku, Long id);

    boolean existsByProductIdAndSizeAndColorNameAndIdNot(Long productId, String size, String colorName, Long id);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);
}