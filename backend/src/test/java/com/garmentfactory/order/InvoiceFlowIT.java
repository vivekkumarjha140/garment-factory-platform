package com.garmentfactory.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.BytesResponse;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import com.garmentfactory.payment.FakeRazorpayConfig;
import com.garmentfactory.support.OrderFixtures;
import com.garmentfactory.support.OrderFixtures.Placed;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Invoice issue, PDF download and the confirmation email, end to end. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@Import(FakeRazorpayConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.bootstrap.admin-email=admin@example.com",
        "app.bootstrap.admin-password=Adm1nStrongPass",
        "app.bootstrap.admin-name=Root Admin",
        "app.razorpay.key-id=rzp_test_key",
        "app.razorpay.key-secret=test_key_secret",
        "app.razorpay.webhook-secret=whsec_test_secret",
        "app.business.name=Test Garments Pvt Ltd",
        "app.business.gstin=06ABCDE1234F1Z5",
        "app.business.address-line1=Plot 1 Industrial Area",
        "app.business.city=Gurugram",
        "app.business.pincode=122001",
        "app.business.state=Haryana",
        "app.business.state-code=06"})
class InvoiceFlowIT {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withCommand("--log-bin-trust-function-creators=1");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private ApiClient api;
    private OrderFixtures fx;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(environment);
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson("admin@example.com", "Adm1nStrongPass"));
        assertThat(login.status()).isEqualTo(200);
        fx = new OrderFixtures(api, jdbc, emails, login.accessToken());
    }

    private Placed paidOrder() throws Exception {
        Placed order = fx.placedOrder(2);
        String razorpayOrder = fx.initiate(order);
        assertThat(fx.captureByWebhook(order, razorpayOrder, OrderFixtures.unique("evt_")).status()).isEqualTo(200);
        return order;
    }

    private void awaitInvoice(Placed order) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM invoices WHERE order_id = ? AND pdf_storage_key IS NOT NULL",
                        Long.class, order.orderId())).isEqualTo(1));
    }

    private boolean confirmationSent(Placed order) {
        return emails.sentTo(order.email()).stream().anyMatch(m -> m.subject().contains(order.orderNumber()));
    }

    private String invoicePath(Placed order) {
        return "/api/v1/orders/" + order.orderNumber() + "/invoice";
    }

    @Test
    void aConfirmedOrderGetsAnInvoiceWithMatchingTotalsAndAConfirmationEmail() throws Exception {
        Placed order = paidOrder();

        awaitInvoice(order);
        Map<String, Object> invoice = jdbc.queryForMap("""
                SELECT invoice_number, gst_type, tax_total, cgst_total, sgst_total, igst_total, grand_total
                  FROM invoices WHERE order_id = ?
                """, order.orderId());
        assertThat(invoice.get("invoice_number").toString()).matches("GF/\\d{2}-\\d{2}/\\d{6}");
        assertThat(invoice.get("gst_type")).isEqualTo("INTRA_STATE");   // seller and buyer are both in Haryana
        assertThat((BigDecimal) invoice.get("igst_total")).isEqualByComparingTo("0.00");
        assertThat(((BigDecimal) invoice.get("cgst_total")).add((BigDecimal) invoice.get("sgst_total")))
                .isEqualByComparingTo((BigDecimal) invoice.get("tax_total"));
        assertThat((BigDecimal) invoice.get("grand_total")).isEqualByComparingTo(order.grandTotal());

        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> confirmationSent(order));
    }

    @Test
    void theCustomerCanDownloadTheInvoiceAsAPdfWithTheLegalDetails() throws Exception {
        Placed order = paidOrder();
        awaitInvoice(order);

        BytesResponse response = api.getBytes(invoicePath(order), order.token());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.header("Content-Type")).startsWith("application/pdf");
        assertThat(new String(response.body(), 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        try (PDDocument pdf = Loader.loadPDF(response.body())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("TAX INVOICE", "06ABCDE1234F1Z5", "Test Garments Pvt Ltd", "6109", "CGST", "SGST", order.orderNumber());
        }
    }

    @Test
    void anUnpaidOrderHasNoInvoice() throws Exception {
        Placed order = fx.placedOrder(1);

        BytesResponse response = api.getBytes(invoicePath(order), order.token());

        assertThat(response.status()).isEqualTo(409);
        assertThat(new String(response.body())).contains("INVOICE_NOT_AVAILABLE");
    }

    @Test
    void someoneElsesInvoiceIsNotFound() throws Exception {
        Placed order = paidOrder();
        awaitInvoice(order);
        String other = fx.registerAndLogin("other-" + java.util.UUID.randomUUID() + "@example.com");

        assertThat(api.getBytes(invoicePath(order), other).status()).isEqualTo(404);
    }

    @Test
    void aRepeatedWebhookNeitherIssuesASecondInvoiceNorSendsASecondEmail() throws Exception {
        Placed order = fx.placedOrder(2);
        String razorpayOrder = fx.initiate(order);
        String eventId = OrderFixtures.unique("evt_");

        assertThat(fx.captureByWebhook(order, razorpayOrder, eventId).status()).isEqualTo(200);
        assertThat(fx.captureByWebhook(order, razorpayOrder, eventId).status()).isEqualTo(200);
        awaitInvoice(order);
        Awaitility.await().atMost(Duration.ofSeconds(20)).until(() -> confirmationSent(order));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoices WHERE order_id = ?", Long.class, order.orderId())).isEqualTo(1);
        assertThat(emails.sentTo(order.email()).stream().filter(m -> m.subject().contains(order.orderNumber())).count()).isEqualTo(1);
    }

    @Test
    void aMissingInvoiceIsRebuiltOnDownload() throws Exception {
        Placed order = paidOrder();
        awaitInvoice(order);
        jdbc.update("DELETE FROM invoices WHERE order_id = ?", order.orderId());

        BytesResponse response = api.getBytes(invoicePath(order), order.token());

        assertThat(response.status()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM invoices WHERE order_id = ?", Long.class, order.orderId())).isEqualTo(1);
    }
}