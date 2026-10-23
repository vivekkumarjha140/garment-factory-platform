package com.garmentfactory.inventory.service;

import com.garmentfactory.inventory.entity.*;
import com.garmentfactory.inventory.exception.InsufficientStockException;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.exception.StockConflictException;
import com.garmentfactory.inventory.exception.StockNotFoundException;
import com.garmentfactory.inventory.repository.RawMaterialRepository;
import com.garmentfactory.inventory.repository.StockMovementRepository;
import com.garmentfactory.inventory.repository.StockReceiptRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class RawMaterialStockService {

    private static final int QTY_SCALE = 3;

    private final RawMaterialRepository rawMaterialRepository;
    private final StockMovementRepository movementRepository;
    private final StockReceiptRepository receiptRepository;

    public RawMaterialStockService(RawMaterialRepository rawMaterialRepository,
                                   StockMovementRepository movementRepository,
                                   StockReceiptRepository receiptRepository) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.movementRepository = movementRepository;
        this.receiptRepository = receiptRepository;
    }

    @Transactional(readOnly = true)
    public RawMaterial get(Long id) { return load(id); }

    @Transactional(readOnly = true)
    public Page<RawMaterial> listLowStock(Pageable pageable) { return rawMaterialRepository.findLowStock(pageable); }

    @Transactional
    public RawMaterial create(String code, String name, RawMaterialCategory category, String uom,
                              BigDecimal lowStockThreshold, BigDecimal unitCost) {
        if (code == null || code.isBlank()) throw new InvalidStockOperationException("Code is required");
        if (name == null || name.isBlank()) throw new InvalidStockOperationException("Name is required");
        if (category == null) throw new InvalidStockOperationException("Category is required");
        if (uom == null || uom.isBlank()) throw new InvalidStockOperationException("Unit of measure is required");
        BigDecimal threshold = lowStockThreshold == null ? BigDecimal.ZERO : lowStockThreshold;
        BigDecimal cost = unitCost == null ? BigDecimal.ZERO : unitCost;
        if (threshold.signum() < 0 || cost.signum() < 0) {
            throw new InvalidStockOperationException("Threshold and unit cost cannot be negative");
        }
        String normalized = code.trim().toUpperCase();
        if (rawMaterialRepository.existsByCode(normalized)) {
            throw new StockConflictException("Raw material code already exists: " + normalized);
        }
        return rawMaterialRepository.save(new RawMaterial(normalized, name.trim(), category, uom.trim(), threshold, cost));
    }

    @Transactional
    public RawMaterial receive(
            RawMaterialReceiptCommand cmd,
            Long actorUserId) {

        if (cmd == null) {
            throw new InvalidStockOperationException(
                    "Receipt command is required");
        }

        if (cmd.rawMaterialId() == null || cmd.rawMaterialId() <= 0) {
            throw new InvalidStockOperationException(
                    "A valid raw material ID is required");
        }

        requirePositive(cmd.quantity());

        if (cmd.unitCost() != null && cmd.unitCost().signum() < 0) {
            throw new InvalidStockOperationException(
                    "Unit cost cannot be negative");
        }

        if (cmd.unitCost() != null
                && cmd.unitCost().scale() > 4
                && cmd.unitCost().stripTrailingZeros().scale() > 4) {
            throw new InvalidStockOperationException(
                    "Unit cost supports at most 4 decimal places");
        }

        if (rawMaterialRepository.receive(
                cmd.rawMaterialId(), cmd.quantity()) == 0) {
            throw new StockNotFoundException(
                    "Raw material not found: " + cmd.rawMaterialId());
        }

        StockReceipt receipt = receiptRepository.saveAndFlush(
                StockReceipt.forRawMaterial(
                        ReceiptNumbers.next(),
                        cmd.rawMaterialId(),
                        cmd.quantity(),
                        cmd.unitCost(),
                        cmd.supplierName(),
                        cmd.supplierReference(),
                        cmd.notes(),
                        Instant.now(),
                        actorUserId));

        RawMaterial after = load(cmd.rawMaterialId());

        movementRepository.save(
                StockMovement.forRawMaterial(
                        cmd.rawMaterialId(),
                        MovementType.RECEIPT,
                        cmd.quantity(),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        after.getOnHand(),
                        FinishedGoodsStockService.REF_RECEIPT,
                        receipt.getPublicId(),
                        "Goods receipt " + receipt.getReceiptNumber(),
                        actorUserId));

        return after;
    }


    @Transactional
    public RawMaterial adjust(Long id, BigDecimal delta, String reason, Long actorUserId) {
        if (id == null || id <= 0) {
            throw new InvalidStockOperationException(
                    "A valid raw material ID is required");
        }

        applyAdjustment(
                id,
                delta,
                reason,
                FinishedGoodsStockService.REF_MANUAL,
                null,
                actorUserId);

        return load(id);
    }

    @Transactional
    public StockMovement adjustForAudit(
            Long id,
            BigDecimal delta,
            String reason,
            String auditPublicId,
            Long actorUserId) {

        if (auditPublicId == null || auditPublicId.isBlank()) {
            throw new InvalidStockOperationException(
                    "Audit reference is required");
        }

        return applyAdjustment(
                id,
                delta,
                reason,
                FinishedGoodsStockService.REF_AUDIT,
                auditPublicId,
                actorUserId);
    }

    private StockMovement applyAdjustment(
            Long id,
            BigDecimal delta,
            String reason,
            String refType,
            String refId,
            Long actorUserId) {

        if (id == null || id <= 0) {
            throw new InvalidStockOperationException(
                    "A valid raw material ID is required");
        }

        if (delta == null || delta.signum() == 0) {
            throw new InvalidStockOperationException(
                    "Adjustment delta cannot be zero");
        }

        checkScale(delta);

        if (reason == null || reason.isBlank()) {
            throw new InvalidStockOperationException(
                    "Reason is required");
        }

        if (rawMaterialRepository.tryAdjust(id, delta) == 0) {
            RawMaterial current = load(id);

            throw new InsufficientStockException(
                    "Adjustment of " + delta
                            + " would reduce on-hand (" + current.getOnHand()
                            + ") below reserved (" + current.getReserved() + ")");
        }

        RawMaterial after = load(id);

        return movementRepository.save(
                StockMovement.forRawMaterial(
                        id,
                        MovementType.ADJUSTMENT,
                        delta,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        after.getOnHand(),
                        refType,
                        refId,
                        reason.trim(),
                        actorUserId));
    }


    private RawMaterial load(Long id) {
        return rawMaterialRepository.findById(id)
                .orElseThrow(() -> new StockNotFoundException("Raw material not found: " + id));
    }

    private static void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) throw new InvalidStockOperationException("Quantity must be greater than zero");
        checkScale(qty);
    }

    private static void checkScale(BigDecimal qty) {
        if (qty.stripTrailingZeros().scale() > QTY_SCALE) {
            throw new InvalidStockOperationException("Quantity supports at most " + QTY_SCALE + " decimal places");
        }
    }
}
