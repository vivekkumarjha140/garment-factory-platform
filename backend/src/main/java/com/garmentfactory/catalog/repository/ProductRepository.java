package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.Product;
import com.garmentfactory.catalog.entity.ProductStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByPublicId(String publicId);

    boolean existsBySlug(String slug);

    /** Row lock (SELECT ... FOR UPDATE): serialises image changes of one product. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.publicId = :publicId")
    Optional<Product> lockByPublicId(@Param("publicId") String publicId);

    /** A product a shopper may see: published, in an active category, with an active (or no) brand. */
    @Query("select p from Product p join fetch p.category c left join fetch p.brand b "
            + "where p.slug = :slug and p.status = :status and c.active = true and (b is null or b.active = true)")
    Optional<Product> findPublicBySlug(@Param("slug") String slug, @Param("status") ProductStatus status);

    /**
     * Staff search over name, slug and SKU. Filters are switched off by their "any" flag, so no
     * parameter is ever null. The sort order is fixed here, never taken from the client.
     */
    @Query(value = "select p from Product p join fetch p.category left join fetch p.brand "
            + "where (:anyStatus = true or p.status = :status) "
            + "and (:anyCategory = true or p.category.id = :categoryId) "
            + "and (lower(p.name) like :pattern or lower(p.slug) like :pattern "
            + "or exists (select 1 from ProductVariant v where v.product = p and lower(v.sku) like :pattern)) "
            + "order by p.createdAt desc, p.id desc",
            countQuery = "select count(p) from Product p "
                    + "where (:anyStatus = true or p.status = :status) "
                    + "and (:anyCategory = true or p.category.id = :categoryId) "
                    + "and (lower(p.name) like :pattern or lower(p.slug) like :pattern "
                    + "or exists (select 1 from ProductVariant v where v.product = p and lower(v.sku) like :pattern))")
    Page<Product> search(@Param("anyStatus") boolean anyStatus, @Param("status") ProductStatus status,
                         @Param("anyCategory") boolean anyCategory, @Param("categoryId") Long categoryId,
                         @Param("pattern") String pattern, Pageable pageable);
}