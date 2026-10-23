package com.garmentfactory.customer.repository;

import com.garmentfactory.customer.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    /** Scoped to the owner: another customer's address looks exactly like one that does not exist. */
    Optional<Address> findByPublicIdAndUserId(String publicId, Long userId);

    long countByUserId(Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.defaultShipping = false where a.userId = :userId and a.defaultShipping = true")
    int clearDefaultShipping(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Address a set a.defaultBilling = false where a.userId = :userId and a.defaultBilling = true")
    int clearDefaultBilling(@Param("userId") Long userId);
}