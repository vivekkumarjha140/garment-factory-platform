package com.garmentfactory.shipping.repository;

import com.garmentfactory.shipping.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    Optional<DeliveryZone> findByCode(String code);

    boolean existsByCode(String code);

    List<DeliveryZone> findByActiveTrueOrderByNameAsc();
}