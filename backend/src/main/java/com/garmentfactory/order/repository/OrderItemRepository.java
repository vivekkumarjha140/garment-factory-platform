package com.garmentfactory.order.repository;

import com.garmentfactory.order.entity.OrderItem;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdInOrderByIdAsc(Collection<Long> orderIds);
    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);
}