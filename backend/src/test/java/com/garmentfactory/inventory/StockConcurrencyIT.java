package com.garmentfactory.inventory;

import static com.garmentfactory.inventory.InventoryTestSupport.INVENTORY;
import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.inventory.entity.StockReservation;
import com.garmentfactory.inventory.service.FinishedGoodsStockService;
import com.garmentfactory.inventory.service.ReservationLine;
import com.garmentfactory.support.ApiClient.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Proves stock is never oversold under concurrent reservations, on a real MySQL 8.4. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "inventory.sweeper.enabled=false"})
class StockConcurrencyIT {

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

    private InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(environment, jdbc);
    }

    /** Fires {@code threads} reservation requests of {@code quantity} at once and returns every response. */
    private List<Response> race(long variantId, int threads, int quantity) throws Exception {
        long baseOrder = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long orderId = baseOrder + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return support.admin("POST", INVENTORY + "/reservations",
                            "{\"orderId\":" + orderId + ",\"variantId\":" + variantId + ",\"quantity\":" + quantity + "}");
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).as("all threads ready").isTrue();
            go.countDown();
            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(90, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static long count(List<Response> responses, int status) {
        return responses.stream().filter(r -> r.status() == status).count();
    }

    @Test
    void tenThreadsRaceForTheLastItemAndExactlyOneWins() throws Exception {
        long variant = support.createStockedVariant(1);

        List<Response> responses = race(variant, 10, 1);

        assertThat(count(responses, 201)).as("successful reservations").isEqualTo(1);
        assertThat(count(responses, 409)).as("rejected with 409 Conflict").isEqualTo(9);
        assertThat(responses).filteredOn(r -> r.status() == 409)
                .allSatisfy(r -> assertThat(r.body()).contains("INSUFFICIENT_STOCK"));
        assertThat(support.onHand(variant)).isEqualTo(1);
        assertThat(support.reserved(variant)).isEqualTo(1);
        // The losers' reservation rows were rolled back together with their failed stock update.
        assertThat(support.reservationCount(variant, "ACTIVE")).isEqualTo(1);
        assertThat(support.ledgerCount(variant, "RESERVATION")).isEqualTo(1);
    }

    @Test
    void fiftyThreadsNeverReserveMoreThanTheStockOnHand() throws Exception {
        long variant = support.createStockedVariant(20);

        List<Response> responses = race(variant, 50, 1);

        assertThat(count(responses, 201)).isEqualTo(20);
        assertThat(count(responses, 409)).isEqualTo(30);
        assertThat(support.reserved(variant)).isEqualTo(20);
        assertThat(support.reserved(variant)).isLessThanOrEqualTo(support.onHand(variant));
        assertThat(support.reservationCount(variant, "ACTIVE")).isEqualTo(20);
        assertThat(support.ledgerCount(variant, "RESERVATION")).isEqualTo(20);
    }

    @Test
    void multiUnitReservationsAreAllOrNothingPerRequest() throws Exception {
        long variant = support.createStockedVariant(5);

        List<Response> responses = race(variant, 4, 2);

        assertThat(count(responses, 201)).isEqualTo(2);
        assertThat(count(responses, 409)).isEqualTo(2);
        assertThat(support.reserved(variant)).isEqualTo(4);
        assertThat(support.onHand(variant) - support.reserved(variant)).isEqualTo(1);
    }

    @Test
    void multiLineReservationsInOppositeOrderNeverDeadlock() throws Exception {
        long a = support.createStockedVariant(20);
        long b = support.createStockedVariant(20);
        int threads = 8;
        long baseOrder = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<List<StockReservation>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                long orderId = baseOrder + i;
                List<ReservationLine> lines = i % 2 == 0
                        ? List.of(new ReservationLine(a, 1), new ReservationLine(b, 1))
                        : List.of(new ReservationLine(b, 1), new ReservationLine(a, 1));
                Callable<List<StockReservation>> task = () -> {
                    ready.countDown();
                    go.await();
                    return stockService.reserveAll(orderId, lines, null);
                };
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<List<StockReservation>> future : futures) {
                assertThat(future.get(90, TimeUnit.SECONDS)).hasSize(2); // a deadlock would surface as an ExecutionException
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(support.reserved(a)).isEqualTo(threads);
        assertThat(support.reserved(b)).isEqualTo(threads);
    }
}