package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortOrderAscIdAsc(Long productId);

    Optional<ProductImage> findByPublicIdAndProductId(String publicId, Long productId);

    long countByProductId(Long productId);

    /** Loads the product and its category with the image, for the "is this product public?" check. */
    @Query("select i from ProductImage i join fetch i.product p join fetch p.category where i.publicId = :publicId")
    Optional<ProductImage> findWithProductByPublicId(@Param("publicId") String publicId);

    @Modifying(flushAutomatically = true)
    @Query("update ProductImage i set i.primaryImage = false where i.product.id = :productId and i.primaryImage = true")
    int clearPrimary(@Param("productId") Long productId);
}