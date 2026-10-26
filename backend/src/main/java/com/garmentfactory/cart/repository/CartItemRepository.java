package com.garmentfactory.cart.repository;

import com.garmentfactory.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    long countByCartId(Long cartId);

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    /** NULL-safe lookup of a cart line (normal item: customDesignId = null). */
    @Query("""
            select ci from CartItem ci
             where ci.cartId = :cartId
               and ci.variantId = :variantId
               and ((:customDesignId is null and ci.customDesignId is null)
                    or ci.customDesignId = :customDesignId)
            """)
    Optional<CartItem> findLine(@Param("cartId") Long cartId,
                                @Param("variantId") Long variantId,
                                @Param("customDesignId") Long customDesignId);

    @Modifying
    @Query("delete from CartItem ci where ci.cartId = :cartId")
    int deleteAllByCart(@Param("cartId") Long cartId);
}