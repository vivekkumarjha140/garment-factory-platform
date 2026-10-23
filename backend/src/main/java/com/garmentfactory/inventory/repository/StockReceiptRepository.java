package com.garmentfactory.inventory.repository;


import com.garmentfactory.inventory.entity.StockReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReceiptRepository extends JpaRepository<StockReceipt, Long> {

    Page<StockReceipt> findAllByOrderByReceivedAtDescIdDesc(Pageable pageable);

    boolean existsByReceiptNumber(String receiptNumber);
}
