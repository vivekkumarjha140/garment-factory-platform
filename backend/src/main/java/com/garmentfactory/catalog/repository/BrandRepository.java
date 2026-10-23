package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.Brand;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByPublicId(String publicId);

    /** Case-insensitive because the column's collation is. */
    boolean existsByNameAndIdNot(String name, Long id);

    List<Brand> findAllByOrderByNameAsc();
}