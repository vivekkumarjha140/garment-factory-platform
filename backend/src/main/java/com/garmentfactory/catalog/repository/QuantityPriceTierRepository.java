package com.garmentfactory.catalog.repository;

import com.garmentfactory.catalog.entity.QuantityPriceTier;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuantityPriceTierRepository extends JpaRepository<QuantityPriceTier, Long> {

    List<QuantityPriceTier> findByProductIdOrderByMinQtyAsc(Long productId);

    void deleteByProductId(Long productId);
}