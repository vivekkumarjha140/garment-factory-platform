
package com.garmentfactory.inventory.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.inventory.entity.*;
import com.garmentfactory.inventory.exception.InsufficientStockException;
import com.garmentfactory.inventory.exception.InvalidStockOperationException;
import com.garmentfactory.inventory.exception.StockNotFoundException;
import com.garmentfactory.inventory.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * All finished-goods stock mutations. Every change = one atomic conditional UPDATE
 * + one immutable stock_movements row, in the same transaction.
 */
@Service
public class FinishedGoodsStockService {

    static final String REF_ORDER = "ORDER";
    static final String REF_RECEIPT = "STOCK_RECEIPT";
    static final String REF_MANUAL = "MANUAL";
    static final String REF_AUDIT = "INVENTORY_AUDIT";
    static final String REF_ORDER_CANCEL = "ORDER_CANCEL";

    private final FinishedGoodsStockRepository stockRepository;
    private final StockMovementRepository movementRepository;
    private final StockReservationRepository reservationRepository;
    private final StockReceiptRepository receiptRepository;
    private final AuditRecorder auditRecorder;
    private final long reservationTtlMinutes;

    public FinishedGoodsStockService(
            FinishedGoodsStockRepository stockRepository,
            StockMovementRepository movementRepository,
            StockReservationRepository reservationRepository,
            StockReceiptRepository receiptRepository,
            AuditRecorder auditRecorder,
            @Value("${inventory.reservation.ttl-minutes:30}")
            long reservationTtlMinutes) {

        this.stockRepository = stockRepository;
        this.movementRepository = movementRepository;
        this.reservationRepository = reservationRepository;
        this.receiptRepository = receiptRepository;
        this.auditRecorder = auditRecorder;
        this.reservationTtlMinutes = reservationTtlMinutes;
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public FinishedGoodsStock getStock(Long variantId) {
        return load(variantId);
    }

    @Transactional(readOnly = true)
    public Page<FinishedGoodsStock> listLowStock(Pageable pageable) {
        return stockRepository.findLowStock(pageable);
    }

    // ------------------------------------------------------------------ setup

    /**
     * Idempotent: creates the stock row for a variant if it does not exist yet.
     */
    @Transactional
    public FinishedGoodsStock initializeStock(
            Long variantId,
            int lowStockThreshold) {

        if (lowStockThreshold < 0) {
            throw new InvalidStockOperationException(
                    "Threshold cannot be negative");
        }

        return stockRepository.findByVariantId(variantId)
                .orElseGet(() -> stockRepository.save(
                        new FinishedGoodsStock(
                                variantId,
                                lowStockThreshold)));
    }

    @Transactional
    public FinishedGoodsStock updateLowStockThreshold(
            Long variantId,
            int lowStockThreshold) {

        if (lowStockThreshold < 0) {
            throw new InvalidStockOperationException(
                    "Threshold cannot be negative");
        }

        FinishedGoodsStock stock = load(variantId);
        int before = stock.getLowStockThreshold();

        stock.setLowStockThreshold(lowStockThreshold);

        audit(
                "STOCK_THRESHOLD_CHANGED",
                variantId,
                "threshold " + before + " -> " + lowStockThreshold);

        return stock;
    }

    // ------------------------------------------------------------ reservations

    @Transactional
    public StockReservation reserve(
            Long orderId,
            Long variantId,
            int quantity,
            Long actorUserId) {

        if (orderId == null || orderId <= 0) {
            throw new InvalidStockOperationException(
                    "A valid order ID is required");
        }

        if (variantId == null || variantId <= 0) {
            throw new InvalidStockOperationException(
                    "A valid variant ID is required");
        }

        requirePositive(quantity);

        return reserveOne(
                orderId,
                variantId,
                quantity,
                expiryFromNow(),
                actorUserId);
    }

    /**
     * All-or-nothing: if any line fails the whole transaction rolls back.
     * Locks in ascending variantId order.
     */
    @Transactional
    public List<StockReservation> reserveAll(
            Long orderId,
            List<ReservationLine> lines,
            Long actorUserId) {

        if (orderId == null || orderId <= 0) {
            throw new InvalidStockOperationException(
                    "A valid order ID is required");
        }

        if (lines == null || lines.isEmpty()) {
            throw new InvalidStockOperationException(
                    "At least one reservation line is required");
        }

        Map<Long, Integer> merged = new TreeMap<>();

        for (ReservationLine line : lines) {
            if (line == null) {
                throw new InvalidStockOperationException(
                        "Reservation line cannot be null");
            }

            Long variantId = line.variantId();

            if (variantId == null || variantId <= 0) {
                throw new InvalidStockOperationException(
                        "A valid variant ID is required");
            }

            requirePositive(line.quantity());

            try {
                merged.merge(
                        variantId,
                        line.quantity(),
                        Math::addExact);
            } catch (ArithmeticException ex) {
                throw new InvalidStockOperationException(
                        "Total reservation quantity exceeds the supported limit");
            }
        }

        Instant expiresAt = expiryFromNow();
        List<StockReservation> result = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
            result.add(reserveOne(
                    orderId,
                    entry.getKey(),
                    entry.getValue(),
                    expiresAt,
                    actorUserId));
        }

        return result;
    }

    /**
     * Releases every ACTIVE reservation of an order
     * (e.g. order cancelled). Returns how many were released.
     */
    @Transactional
    public int releaseForOrder(
            Long orderId,
            String reason,
            Long actorUserId) {

        List<StockReservation> active = sortedActive(orderId);

        for (StockReservation r : active) {
            r.markReleased();
            reservationRepository.saveAndFlush(r);

            if (stockRepository.tryRelease(
                    r.getVariantId(),
                    r.getQuantity()) == 0) {

                throw new IllegalStateException(
                        "Stock inconsistency while releasing reservation "
                                + r.getPublicId());
            }

            FinishedGoodsStock after = load(r.getVariantId());

            record(
                    r.getVariantId(),
                    MovementType.RELEASE,
                    0,
                    -r.getQuantity(),
                    0,
                    after.getOnHand(),
                    REF_ORDER,
                    String.valueOf(orderId),
                    reason == null
                            ? "Reservation released"
                            : reason,
                    actorUserId);
        }

        return active.size();
    }

    /**
     * Turns ACTIVE reservations into real deductions
     * (e.g. order shipped / dispatched).
     */
    @Transactional
    public int consumeForOrder(
            Long orderId,
            Long actorUserId) {

        List<StockReservation> active = sortedActive(orderId);

        for (StockReservation r : active) {
            r.markConsumed();
            reservationRepository.saveAndFlush(r);

            if (stockRepository.tryDeductReserved(
                    r.getVariantId(),
                    r.getQuantity()) == 0) {

                throw new IllegalStateException(
                        "Stock inconsistency while consuming reservation "
                                + r.getPublicId());
            }

            FinishedGoodsStock after = load(r.getVariantId());

            record(
                    r.getVariantId(),
                    MovementType.DEDUCTION,
                    -r.getQuantity(),
                    -r.getQuantity(),
                    0,
                    after.getOnHand(),
                    REF_ORDER,
                    String.valueOf(orderId),
                    "Reservation consumed",
                    actorUserId);
        }

        return active.size();
    }

    /**
     * Puts the stock of a cancelled order back (it had already been deducted). Idempotent: once a ledger row with
     * reference ORDER_CANCEL/<orderId> exists, nothing more is returned. Returns how many lines were returned.
     */
    @Transactional
    public int restockForOrder(
            Long orderId,
            String reason,
            Long actorUserId) {

        String ref = String.valueOf(orderId);

        if (movementRepository.existsByReferenceTypeAndReferenceId(REF_ORDER_CANCEL, ref)) {
            return 0;
        }

        List<StockReservation> consumed = new ArrayList<>(
                reservationRepository.findByOrderIdAndStatus(orderId, ReservationStatus.CONSUMED));

        consumed.sort(
                Comparator.comparing(StockReservation::getVariantId)
                        .thenComparing(StockReservation::getId));

        for (StockReservation r : consumed) {
            applyAdjustment(
                    r.getVariantId(),
                    r.getQuantity(),
                    reason,
                    REF_ORDER_CANCEL,
                    ref,
                    actorUserId);
        }

        return consumed.size();
    }


    // ------------------------------------------------- receipts / adjustments

    @Transactional
    public FinishedGoodsStock receive(
            FinishedGoodsReceiptCommand cmd,
            Long actorUserId) {

        requirePositive(cmd.quantity());

        if (stockRepository.receive(
                cmd.variantId(),
                cmd.quantity()) == 0) {

            throw new StockNotFoundException(
                    "No stock record for variant " + cmd.variantId());
        }

        StockReceipt receipt = receiptRepository.saveAndFlush(
                StockReceipt.forFinishedGood(
                        ReceiptNumbers.next(),
                        cmd.variantId(),
                        cmd.quantity(),
                        cmd.unitCost(),
                        cmd.supplierName(),
                        cmd.supplierReference(),
                        cmd.notes(),
                        Instant.now(),
                        actorUserId));

        FinishedGoodsStock after = load(cmd.variantId());

        record(
                cmd.variantId(),
                MovementType.RECEIPT,
                cmd.quantity(),
                0,
                0,
                after.getOnHand(),
                REF_RECEIPT,
                receipt.getPublicId(),
                "Goods receipt " + receipt.getReceiptNumber(),
                actorUserId);

        audit(
                "STOCK_RECEIVED",
                cmd.variantId(),
                "quantity=" + cmd.quantity()
                        + ", receipt=" + receipt.getReceiptNumber());

        return after;
    }

    /**
     * Manual correction. delta may be negative; reason is mandatory.
     */
    @Transactional
    public FinishedGoodsStock adjust(
            Long variantId,
            int delta,
            String reason,
            Long actorUserId) {

        applyAdjustment(
                variantId,
                delta,
                reason,
                REF_MANUAL,
                null,
                actorUserId);

        FinishedGoodsStock after = load(variantId);

        audit(
                "STOCK_ADJUSTED",
                variantId,
                "delta=" + delta
                        + ", onHand=" + after.getOnHand()
                        + ", reason=" + AuditText.shorten(reason, 500));

        return after;
    }

    /**
     * Same as adjust(), but the ledger row points at the inventory audit
     * and returns the ledger row.
     *
     * The audit completion writes its own audit-log entry,
     * so no per-line entry is written here.
     */
    @Transactional
    public StockMovement adjustForAudit(
            Long variantId,
            int delta,
            String reason,
            String auditPublicId,
            Long actorUserId) {

        return applyAdjustment(
                variantId,
                delta,
                reason,
                REF_AUDIT,
                auditPublicId,
                actorUserId);
    }

    /**
     * Moves unreserved sellable stock into the damaged bucket.
     */
    @Transactional
    public FinishedGoodsStock markDamaged(
            Long variantId,
            int quantity,
            String reason,
            Long actorUserId) {

        requirePositive(quantity);
        requireReason(reason);

        if (stockRepository.tryMarkDamaged(
                variantId,
                quantity) == 0) {

            throw conflictOrNotFound(variantId, quantity);
        }

        FinishedGoodsStock after = load(variantId);

        record(
                variantId,
                MovementType.DAMAGE,
                -quantity,
                0,
                quantity,
                after.getOnHand(),
                REF_MANUAL,
                null,
                reason.trim(),
                actorUserId);

        audit(
                "STOCK_DAMAGED",
                variantId,
                "quantity=" + quantity
                        + ", reason=" + AuditText.shorten(reason, 500));

        return after;
    }

    // ---------------------------------------------------------------- helpers

    private StockMovement applyAdjustment(
            Long variantId,
            int delta,
            String reason,
            String refType,
            String refId,
            Long actorUserId) {

        if (delta == 0) {
            throw new InvalidStockOperationException(
                    "Adjustment delta cannot be zero");
        }

        requireReason(reason);

        if (stockRepository.tryAdjust(variantId, delta) == 0) {
            FinishedGoodsStock current = load(variantId);

            throw new InsufficientStockException(
                    "Adjustment of " + delta
                            + " would reduce on-hand ("
                            + current.getOnHand()
                            + ") below reserved ("
                            + current.getReserved()
                            + ")");
        }

        FinishedGoodsStock after = load(variantId);

        return movementRepository.save(
                StockMovement.forFinishedGood(
                        variantId,
                        MovementType.ADJUSTMENT,
                        delta,
                        0,
                        0,
                        after.getOnHand(),
                        refType,
                        refId,
                        reason.trim(),
                        actorUserId));
    }

    private StockReservation reserveOne(
            Long orderId,
            Long variantId,
            int qty,
            Instant expiresAt,
            Long actorUserId) {

        // Insert the reservation row FIRST, then touch the stock row
        // (lock-order rule vs. the sweeper).
        StockReservation reservation =
                reservationRepository.saveAndFlush(
                        new StockReservation(
                                orderId,
                                variantId,
                                qty,
                                expiresAt));

        if (stockRepository.tryReserve(variantId, qty) == 0) {
            throw conflictOrNotFound(
                    variantId,
                    qty); // rolls back the reservation insert too
        }

        FinishedGoodsStock after = load(variantId);

        record(
                variantId,
                MovementType.RESERVATION,
                0,
                qty,
                0,
                after.getOnHand(),
                REF_ORDER,
                String.valueOf(orderId),
                "Stock reserved",
                actorUserId);

        return reservation;
    }

    private RuntimeException conflictOrNotFound(
            Long variantId,
            int requested) {

        return stockRepository.findByVariantId(variantId)
                .<RuntimeException>map(
                        s -> InsufficientStockException.forVariant(
                                variantId,
                                requested,
                                s.getAvailable()))
                .orElseGet(
                        () -> new StockNotFoundException(
                                "No stock record for variant " + variantId));
    }

    private FinishedGoodsStock load(Long variantId) {
        return stockRepository.findByVariantId(variantId)
                .orElseThrow(
                        () -> new StockNotFoundException(
                                "No stock record for variant " + variantId));
    }

    private List<StockReservation> sortedActive(Long orderId) {
        List<StockReservation> list = new ArrayList<>(
                reservationRepository.findByOrderIdAndStatus(
                        orderId,
                        ReservationStatus.ACTIVE));

        list.sort(
                Comparator.comparing(StockReservation::getVariantId)
                        .thenComparing(StockReservation::getId));

        return list;
    }

    private void record(
            Long variantId,
            MovementType type,
            int onHandDelta,
            int reservedDelta,
            int damagedDelta,
            int balanceAfter,
            String refType,
            String refId,
            String reason,
            Long actorUserId) {

        movementRepository.save(
                StockMovement.forFinishedGood(
                        variantId,
                        type,
                        onHandDelta,
                        reservedDelta,
                        damagedDelta,
                        balanceAfter,
                        refType,
                        refId,
                        reason,
                        actorUserId));
    }

    private void audit(
            String action,
            Long variantId,
            String details) {

        auditRecorder.record(
                AuditEvent.of(
                        action,
                        "STOCK",
                        String.valueOf(variantId),
                        details));
    }

    private Instant expiryFromNow() {
        return Instant.now().plus(
                Duration.ofMinutes(reservationTtlMinutes));
    }

    private static void requirePositive(int qty) {
        if (qty <= 0) {
            throw new InvalidStockOperationException(
                    "Quantity must be greater than zero");
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new InvalidStockOperationException(
                    "Reason is required");
        }
    }
}
