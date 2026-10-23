package com.garmentfactory.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.garmentfactory.support.ApiClient;
import com.garmentfactory.support.ApiClient.Response;
import com.garmentfactory.support.CapturingEmailSender;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

/** Customer profile and address book against a real MySQL 8.4. Requires Docker. */
@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerApiIT {

    private static final String PASSWORD = "Str0ngPassw0rd";
    private static final String ADDRESSES = "/api/v1/customers/me/addresses";
    private static final Pattern ID = Pattern.compile("\"id\":\"([^\"]+)\"");

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired
    private Environment environment;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CapturingEmailSender emails;

    private ApiClient api;

    private record Customer(String email, String accessToken) {
    }

    @BeforeEach
    void setUp() {
        api = new ApiClient(environment);
    }

    private Customer newCustomer() throws Exception {
        String email = "customer-" + UUID.randomUUID() + "@example.com";
        assertThat(api.post("/api/v1/auth/register", ApiClient.registerJson(email, PASSWORD)).status()).isEqualTo(202);
        api.post("/api/v1/auth/verify-email", "{\"token\":\"" + emails.latestTokenTo(email) + "\"}");
        Response login = api.post("/api/v1/auth/login", ApiClient.loginJson(email, PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        return new Customer(email, login.accessToken());
    }

    private Response as(Customer customer, String method, String path, String body) throws Exception {
        return api.send(method, path, body, Map.of("Authorization", "Bearer " + customer.accessToken()));
    }

    private static String addressJson(String label, String state, String pincode) {
        return "{\"label\":\"" + label + "\",\"recipientName\":\"Asha Verma\",\"phone\":\"+919876543210\","
                + "\"line1\":\"12 MI Road\",\"line2\":null,\"city\":\"Jaipur\",\"state\":\"" + state
                + "\",\"pincode\":\"" + pincode + "\"}";
    }

    private String createAddress(Customer customer, String label) throws Exception {
        Response created = as(customer, "POST", ADDRESSES, addressJson(label, "Rajasthan", "302001"));
        assertThat(created.status()).as("create address: %s", created.body()).isEqualTo(201);
        Matcher matcher = ID.matcher(created.body());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private int defaultShippingRows(Customer customer) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE is_default_shipping = 1 AND user_id = "
                + "(SELECT id FROM users WHERE email = ?)", Integer.class, customer.email());
    }

    @Test
    void profileIsCreatedOnFirstUseAndCanBeUpdated() throws Exception {
        Customer customer = newCustomer();

        Response first = as(customer, "GET", "/api/v1/customers/me", null);
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.body()).contains(customer.email()).contains("\"customerType\":\"RETAIL\"")
                .contains("\"marketingOptIn\":false");

        Response updated = as(customer, "PATCH", "/api/v1/customers/me",
                "{\"fullName\":\"Asha Verma\",\"phone\":\"+919876543210\",\"marketingOptIn\":true}");
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("Asha Verma").contains("+919876543210").contains("\"marketingOptIn\":true");
        assertThat(as(customer, "GET", "/api/v1/users/me", null).body()).contains("Asha Verma");

        Response cleared = as(customer, "PATCH", "/api/v1/customers/me", "{\"phone\":\"\"}");
        assertThat(cleared.body()).contains("\"phone\":null").contains("Asha Verma").contains("\"marketingOptIn\":true");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM customer_profiles cp JOIN users u ON u.id = cp.user_id "
                + "WHERE u.email = ?", Integer.class, customer.email())).isEqualTo(1);
    }

    @Test
    void invalidProfileInputIsRejected() throws Exception {
        Customer customer = newCustomer();

        assertThat(as(customer, "PATCH", "/api/v1/customers/me", "{\"fullName\":\"   \"}").status()).isEqualTo(400);
        assertThat(as(customer, "PATCH", "/api/v1/customers/me", "{\"phone\":\"12-34\"}").status()).isEqualTo(400);
    }

    @Test
    void addressBookEndpointsRequireSignIn() throws Exception {
        assertThat(api.get(ADDRESSES, null).status()).isEqualTo(401);
        assertThat(api.get("/api/v1/customers/me", null).status()).isEqualTo(401);
    }

    @Test
    void addressCanBeCreatedUpdatedListedAndDeleted() throws Exception {
        Customer customer = newCustomer();
        String id = createAddress(customer, "Home");

        Response updated = as(customer, "PUT", ADDRESSES + "/" + id, addressJson("Office", "maharashtra", "400001"));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).contains("\"label\":\"Office\"").contains("\"state\":\"Maharashtra\"")
                .contains("\"pincode\":\"400001\"");

        assertThat(as(customer, "GET", ADDRESSES, null).body()).contains(id);
        assertThat(as(customer, "DELETE", ADDRESSES + "/" + id, null).status()).isEqualTo(204);
        assertThat(as(customer, "GET", ADDRESSES, null).body()).isEqualTo("[]");
    }

    @Test
    void firstAddressIsDefaultAndDefaultsMoveOnRequestAndOnDelete() throws Exception {
        Customer customer = newCustomer();
        String first = createAddress(customer, "Home");
        String second = createAddress(customer, "Office");

        assertThat(as(customer, "GET", ADDRESSES, null).body()).contains("\"defaultShipping\":true");
        assertThat(defaultShippingRows(customer)).isEqualTo(1);

        Response moved = as(customer, "POST", ADDRESSES + "/" + second + "/default-shipping", null);
        assertThat(moved.status()).isEqualTo(200);
        assertThat(moved.body()).contains("\"defaultShipping\":true").contains("\"defaultBilling\":false");
        assertThat(defaultShippingRows(customer)).isEqualTo(1);

        assertThat(as(customer, "DELETE", ADDRESSES + "/" + second, null).status()).isEqualTo(204);
        Response remaining = as(customer, "GET", ADDRESSES, null);
        assertThat(remaining.body()).contains(first).contains("\"defaultShipping\":true").contains("\"defaultBilling\":true");
        assertThat(defaultShippingRows(customer)).isEqualTo(1);
    }

    @Test
    void invalidAddressesAreRejected() throws Exception {
        Customer customer = newCustomer();

        Response badPincode = as(customer, "POST", ADDRESSES, addressJson("Home", "Rajasthan", "012345"));
        assertThat(badPincode.status()).isEqualTo(400);
        assertThat(badPincode.body()).contains("VALIDATION_FAILED").contains("pincode");

        Response badState = as(customer, "POST", ADDRESSES, addressJson("Home", "Atlantis", "302001"));
        assertThat(badState.status()).isEqualTo(400);
        assertThat(badState.body()).contains("INVALID_STATE");

        assertThat(as(customer, "POST", ADDRESSES, "{}").status()).isEqualTo(400);
        assertThat(as(customer, "GET", "/api/v1/customers/reference/states", null).body()).contains("Rajasthan");
    }

    @Test
    void customersCannotSeeOrChangeEachOthersAddresses() throws Exception {
        Customer owner = newCustomer();
        Customer intruder = newCustomer();
        String id = createAddress(owner, "Home");

        assertThat(as(intruder, "GET", ADDRESSES, null).body()).isEqualTo("[]");
        for (String[] call : List.of(new String[]{"PUT", ADDRESSES + "/" + id},
                new String[]{"DELETE", ADDRESSES + "/" + id},
                new String[]{"POST", ADDRESSES + "/" + id + "/default-shipping"},
                new String[]{"POST", ADDRESSES + "/" + id + "/default-billing"})) {
            Response response = as(intruder, call[0], call[1], call[0].equals("PUT") ? addressJson("X", "Goa", "403001") : null);
            assertThat(response.status()).as("%s %s", call[0], call[1]).isEqualTo(404);
            assertThat(response.body()).contains("ADDRESS_NOT_FOUND");
        }
        assertThat(as(owner, "GET", ADDRESSES, null).body()).contains(id).contains("Home");
    }

    @Test
    void atMostTenAddressesCanBeSaved() throws Exception {
        Customer customer = newCustomer();
        for (int i = 0; i < 10; i++) {
            createAddress(customer, "Address " + i);
        }

        Response eleventh = as(customer, "POST", ADDRESSES, addressJson("One too many", "Goa", "403001"));

        assertThat(eleventh.status()).isEqualTo(409);
        assertThat(eleventh.body()).contains("ADDRESS_LIMIT_REACHED");
    }

    @Test
    void concurrentCreationsRespectTheLimitAndLeaveExactlyOneDefault() throws Exception {
        Customer customer = newCustomer();
        as(customer, "GET", "/api/v1/customers/me", null);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = IntStream.range(0, 15)
                    .mapToObj(i -> pool.submit(() -> as(customer, "POST", ADDRESSES, addressJson("A" + i, "Goa", "403001")).status()))
                    .toList();
            int created = 0;
            int rejected = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    rejected++;
                }
            }

            assertThat(created).isEqualTo(10);
            assertThat(rejected).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(defaultShippingRows(customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM addresses WHERE user_id = "
                + "(SELECT id FROM users WHERE email = ?)", Integer.class, customer.email())).isEqualTo(10);
    }

    @Test
    void theDatabaseItselfRejectsASecondDefaultAddress() throws Exception {
        Customer customer = newCustomer();
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, customer.email());
        String insert = "INSERT INTO addresses (public_id, user_id, recipient_name, phone, line1, city, state, pincode, "
                + "is_default_shipping) VALUES (UUID(), ?, 'A', '9876543210', 'L1', 'C', 'Goa', '403001', 1)";
        jdbc.update(insert, userId);

        assertThatThrownBy(() -> jdbc.update(insert, userId)).isInstanceOf(DataIntegrityViolationException.class);
    }
}