package com.garmentfactory.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.inventory.api.InventoryApi;
import com.garmentfactory.inventory.api.StockLine;
import com.garmentfactory.shared.exception.ApiException;
import java.util.List;
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

/** The public InventoryApi that other modules (orders) will call. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "inventory.sweeper.enabled=false"})
class InventoryApiBoundaryIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private InventoryApi inventoryApi;

    private InventoryTestSupport support;

    @BeforeEach
    void setUp() throws Exception {
        support = new InventoryTestSupport(environment, jdbc);
    }

    @Test
    void reservingSeveralLinesIsAllOrNothing() throws Exception {
        long enough = support.createStockedVariant(5);
        long scarce = support.createStockedVariant(1);
        long orderId = System.nanoTime();

        assertThatThrownBy(() -> inventoryApi.reserveForOrder(orderId,
                List.of(new StockLine(enough, 2), new StockLine(scarce, 2)), null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("code", "INSUFFICIENT_STOCK");

        assertThat(support.reserved(enough)).isZero();
        assertThat(support.reserved(scarce)).isZero();
        assertThat(support.reservationCount(enough, "ACTIVE")).isZero();
    }

    @Test
    void consumingAnOrderTurnsReservedStockIntoADeduction() throws Exception {
        long variant = support.createStockedVariant(5);
        long orderId = System.nanoTime();
        inventoryApi.reserveForOrder(orderId, List.of(new StockLine(variant, 3)), null);
        assertThat(inventoryApi.availability(variant).orElseThrow().available()).isEqualTo(2);

        int consumed = inventoryApi.consumeForOrder(orderId, null);

        assertThat(consumed).isEqualTo(1);
        assertThat(support.onHand(variant)).isEqualTo(2);
        assertThat(support.reserved(variant)).isZero();
        assertThat(support.ledgerCount(variant, "DEDUCTION")).isEqualTo(1);
        assertThat(inventoryApi.availability(List.of(variant)).get(variant).available()).isEqualTo(2);
    }
}