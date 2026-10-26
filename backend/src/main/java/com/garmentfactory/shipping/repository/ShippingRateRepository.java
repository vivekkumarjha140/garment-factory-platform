package com.garmentfactory.shipping.repository;

import com.garmentfactory.shipping.entity.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShippingRateRepository extends JpaRepository<ShippingRate, Long> {

    List<ShippingRate> findByZoneIdOrderByMinWeightGramsAsc(Long zoneId);

    /** Active slab(s) covering weight w, i.e. min <= w < max. Service takes the first result. */
    @Query("""
            select r from ShippingRate r
             where r.zoneId = :zoneId
               and r.active = true
               and r.minWeightGrams <= :weight
               and r.maxWeightGrams > :weight
             order by r.minWeightGrams desc
            """)
    List<ShippingRate> findMatchingSlabs(@Param("zoneId") Long zoneId, @Param("weight") int weight);
}