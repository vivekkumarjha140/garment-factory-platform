package com.garmentfactory.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.inventory.entity.StockReservation;
import com.garmentfactory.inventory.repository.StockReservationRepository;
import com.garmentfactory.inventory.service.FinishedGoodsStockService;
import com.garmentfactory.inventory.service.ReservationExpiryService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Reservation expiry sweeper against a real MySQL 8.4 (SELECT ... FOR UPDATE SKIP LOCKED). Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "inventory.sweeper.enabled=false"})
class ReservationSweeperIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL =
            new MySQLContainer("mysql:8.4")
                    .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private FinishedGoodsStockService stockService;
    @Autowired
    private ReservationExpiryService expiryService;
    @Autowired
    private StockReservationRepository reservationRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(environment, jdbc);
        // Start every test from a clean slate: release anything a previous test left expired.
        while (expiryService.expireBatch(100) > 0) {
            // keep draining
        }
    }

    private void backdate(String reservationPublicId, int minutesAgo) {
        jdbc.update("UPDATE stock_reservations SET expires_at = DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? MINUTE) WHERE public_id = ?",
                minutesAgo, reservationPublicId);
    }

    private String status(String reservationPublicId) {
        return jdbc.queryForObject("SELECT status FROM stock_reservations WHERE public_id = ?", String.class, reservationPublicId);
    }

    @Test
    void expiredReservationsAreReleasedAndLedgered() throws Exception {
        long variant = support.createStockedVariant(10);
        StockReservation expired = stockService.reserve(System.nanoTime(), variant, 3, null);
        StockReservation fresh = stockService.reserve(System.nanoTime() + 1, variant, 2, null);
        backdate(expired.getPublicId(), 1);
        assertThat(support.reserved(variant)).isEqualTo(5);

        int processed = expiryService.expireBatch(100);

        assertThat(processed).isEqualTo(1);
        assertThat(status(expired.getPublicId())).isEqualTo("EXPIRED");
        assertThat(status(fresh.getPublicId())).isEqualTo("ACTIVE");
        assertThat(support.reserved(variant)).isEqualTo(2);
        assertThat(support.onHand(variant)).isEqualTo(10);

        Map<String, Object> release = jdbc.queryForMap(
                "SELECT reserved_delta, actor_user_id, reason FROM stock_movements WHERE variant_id = ? AND movement_type = 'RELEASE'",
                variant);
        assertThat((BigDecimal) release.get("reserved_delta")).isEqualByComparingTo("-3");
        assertThat(release.get("actor_user_id")).isNull();
        assertThat(release.get("reason")).isEqualTo("Reservation expired");

        stockService.releaseForOrder(jdbc.queryForObject(
                "SELECT order_id FROM stock_reservations WHERE public_id = ?", Long.class, fresh.getPublicId()), "test cleanup", null);
    }

    @Test
    void reservationExpiryIsStoredInUtc() throws Exception {
        long variant = support.createStockedVariant(1);
        long orderId = System.nanoTime();
        StockReservation reservation = stockService.reserve(orderId, variant, 1, null);

        Integer minutesLeft = jdbc.queryForObject(
                "SELECT TIMESTAMPDIFF(MINUTE, UTC_TIMESTAMP(6), expires_at) FROM stock_reservations WHERE public_id = ?",
                Integer.class, reservation.getPublicId());

        // Default TTL is 30 minutes. A multi-hour offset here means the JDBC time zone is not UTC.
        assertThat(minutesLeft).as("minutes until expiry, measured in UTC").isBetween(28, 30);
        stockService.releaseForOrder(orderId, "test cleanup", null);
    }

    @Test
    void skipLockedLetsTwoSweepersShareTheWorkWithoutBlockingOrDoubleReleasing() throws Exception {
        long variant = support.createStockedVariant(10);
        int[] minutesAgo = {40, 30, 20, 10}; // oldest first: the first sweeper locks the two oldest
        List<StockReservation> reservations = new java.util.ArrayList<>();
        for (int i = 0; i < minutesAgo.length; i++) {
            StockReservation r = stockService.reserve(System.nanoTime() + i, variant, 1, null);
            backdate(r.getPublicId(), minutesAgo[i]);
            reservations.add(r);
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // Sweeper A: locks two expired rows and keeps its transaction open.
            Future<List<String>> holder = pool.submit(() -> tx.execute(status -> {
                List<String> held = reservationRepository.lockExpiredBatch(2).stream()
                        .map(StockReservation::getPublicId).toList();
                locked.countDown();
                await(release);
                return held;
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).as("sweeper A holds its locks").isTrue();

            // Sweeper B: must skip A's rows, finish promptly and expire only the other two.
            int processedByB = expiryService.expireBatch(100);
            release.countDown();
            List<String> heldByA = holder.get(30, TimeUnit.SECONDS);

            assertThat(heldByA).hasSize(2);
            assertThat(processedByB).isEqualTo(2);
            for (StockReservation r : reservations) {
                String expected = heldByA.contains(r.getPublicId()) ? "ACTIVE" : "EXPIRED";
                assertThat(status(r.getPublicId())).as("status of %s", r.getPublicId()).isEqualTo(expected);
            }
            assertThat(support.reserved(variant)).isEqualTo(2);

            // Once A's locks are gone, the next sweep picks up the remaining two.
            assertThat(expiryService.expireBatch(100)).isEqualTo(2);
            assertThat(support.reserved(variant)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test to release the lock");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }
}