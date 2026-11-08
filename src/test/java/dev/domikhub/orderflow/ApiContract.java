package dev.domikhub.orderflow;

import dev.domikhub.orderflow.auth.JwtTokens;
import dev.domikhub.orderflow.user.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class ApiContract {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtTokens tokens;
    @Autowired PasswordEncoder passwords;
    final JsonMapper json = JsonMapper.builder().build();
    final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    static final String PASSWORD = "TestPassword2026!";
    static final String SEED_HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);
    String admin, alice, bob;
    long productId;

    @BeforeEach void setup() throws Exception {
        for (String table : List.of("order_items", "shop_orders", "cart_items", "products", "app_users")) jdbc.update("DELETE FROM " + table);
        admin = seed("admin@example.com", AppUser.Role.ADMIN);
        alice = seed("alice@example.com", AppUser.Role.USER);
        bob = seed("bob@example.com", AppUser.Role.USER);
        productId = product("KEYBOARD", "Keyboard", "10.10", 10);
    }

    @Test void registrationAndLoginHashPasswordsAndNormalizeEmail() throws Exception {
        var data = Map.of("email", "New@Example.com", "displayName", "New Student", "password", PASSWORD, "role", "ADMIN");
        assertThat(request("POST", "/api/auth/register", null, data).status()).isEqualTo(201);
        var user = users.findByEmail("new@example.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(AppUser.Role.USER);
        assertThat(user.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, user.getPasswordHash())).isTrue();
        assertThat(request("POST", "/api/auth/register", null, data).status()).isEqualTo(409);
        var login = request("POST", "/api/auth/login", null, Map.of("email", "NEW@example.com", "password", PASSWORD));
        assertThat(login.status()).isEqualTo(200);
        var me = request("GET", "/api/auth/me", login.body().get("accessToken").asText(), null);
        assertThat(me.raw()).doesNotContain("password", "Hash");
        assertThat(request("POST", "/api/auth/login", null, Map.of("email", "new@example.com", "password", "wrong-password")).status()).isEqualTo(401);
    }
    @Test void requiresAuthenticationAndAdminRole() throws Exception {
        assertThat(request("GET", "/api/products", null, null).status()).isEqualTo(401);
        assertThat(request("GET", "/api/cart", "invalid-token", null).status()).isEqualTo(401);
        assertThat(request("POST", "/api/admin/products", alice, productData("NOPE", "Other", "4.00", 2)).status()).isEqualTo(403);
        assertThat(request("POST", "/api/admin/products/" + productId + "/stock", alice, Map.of("delta", 1)).status()).isEqualTo(403);
    }
    @Test void validatesPricesSkuPaginationAndSupportsFilteringAndSorting() throws Exception {
        product("MOUSE", "Mouse", "2.20", 3);
        assertThat(request("POST", "/api/admin/products", admin, productData("KEYBOARD", "Duplicate", "1.00", 0)).status()).isEqualTo(409);
        for (String price : List.of("0", "-1.00", "1.999", "10000000.00"))
            assertThat(request("POST", "/api/admin/products", admin, productData("INVALID", "Invalid", price, 0)).status()).isEqualTo(400);
        var list = request("GET", "/api/products?sort=priceAsc&size=1", alice, null);
        assertThat(list.body().get("content").get(0).get("sku").asText()).isEqualTo("MOUSE");
        assertThat(list.body().get("totalElements").asInt()).isEqualTo(2);
        assertThat(request("GET", "/api/products?query=key&minPrice=10&maxPrice=11", alice, null).body().get("totalElements").asInt()).isEqualTo(1);
        for (String query : List.of("size=101", "page=-1", "sort=random", "minPrice=20&maxPrice=1"))
            assertThat(request("GET", "/api/products?" + query, alice, null).status()).isEqualTo(400);
    }
    @Test void stockAdjustmentsAreValidatedAndDatabaseRejectsNegativeStock() throws Exception {
        assertThat(adjust(productId, -11).status()).isEqualTo(409);
        assertThat(adjust(productId, 0).status()).isEqualTo(400);
        assertThat(adjust(productId, 5).body().get("available").asLong()).isEqualTo(15);
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET stock = -1 WHERE id = ?", productId))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(stock(productId)).isEqualTo(15);
    }
    @Test void cartSetsAbsoluteQuantityIsPrivateAndDoesNotReserveStock() throws Exception {
        cart(alice, productId, 2); cart(alice, productId, 3);
        var cart = request("GET", "/api/cart", alice, null).body();
        assertThat(cart.get("items").size()).isEqualTo(1);
        assertThat(cart.get("items").get(0).get("quantity").asInt()).isEqualTo(3);
        assertThat(cart.get("estimatedTotal").decimalValue()).isEqualByComparingTo("30.30");
        assertThat(stock(productId)).isEqualTo(10);
        assertThat(request("GET", "/api/cart", bob, null).body().get("items").isEmpty()).isTrue();
        assertThat(request("PUT", "/api/cart/items/" + productId, alice, Map.of("quantity", 0)).status()).isEqualTo(400);
        assertThat(request("PUT", "/api/cart/items/" + productId, alice, Map.of("quantity", 100)).status()).isEqualTo(400);
        assertThat(request("DELETE", "/api/cart/items/" + productId, alice, null).status()).isEqualTo(204);
    }
    @Test void checkoutUsesExactMoneyClearsCartAndPreservesPriceSnapshot() throws Exception {
        long second = product("CABLE", "Cable", "0.20", 5);
        cart(alice, productId, 3); cart(alice, second, 2);
        var order = checkout(alice, UUID.randomUUID()).body();
        assertThat(order.get("total").decimalValue()).isEqualByComparingTo("30.70");
        assertThat(order.get("currency").asText()).isEqualTo("KZT");
        assertThat(stock(productId)).isEqualTo(7);
        assertThat(stock(second)).isEqualTo(3);
        assertThat(request("GET", "/api/cart", alice, null).body().get("items").isEmpty()).isTrue();
        assertThat(update(productId, "Renamed", "999.00", true).status()).isEqualTo(200);
        var saved = request("GET", "/api/orders/" + order.get("id").asLong(), alice, null).body();
        assertThat(saved.get("total").decimalValue()).isEqualByComparingTo("30.70");
        assertThat(saved.get("items").get(0).get("name").asText()).isEqualTo("Keyboard");
        assertThat(saved.get("items").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("10.10");
    }
    @Test void idempotencyReplaysOriginalOrderWithoutConsumingNewCart() throws Exception {
        cart(alice, productId, 2);
        UUID key = UUID.randomUUID();
        var first = checkout(alice, key);
        assertThat(first.status()).isEqualTo(201);
        cart(alice, productId, 1);
        var replay = checkout(alice, key);
        assertThat(replay.status()).isEqualTo(200);
        assertThat(replay.body().get("id")).isEqualTo(first.body().get("id"));
        assertThat(replay.headers().firstValue("Idempotency-Replayed")).contains("true");
        assertThat(stock(productId)).isEqualTo(8);
        assertThat(request("GET", "/api/cart", alice, null).body().get("items").get(0).get("quantity").asInt()).isEqualTo(1);
        assertThat(checkout(alice, UUID.randomUUID()).status()).isEqualTo(201);
    }
    @Test void idempotencyRejectsDifferentAddress() throws Exception {
        cart(alice, productId, 1);
        UUID key = UUID.randomUUID(); checkout(alice, key);
        assertThat(request("POST", "/api/orders", alice, Map.of("shippingAddress", "Other address"), key.toString()).status()).isEqualTo(409);
    }
    @Test void failedCheckoutRollsBackEarlierStockChangesAndKeepsCart() throws Exception {
        long unavailable = product("EMPTY", "Unavailable", "4.00", 0);
        cart(alice, productId, 2); cart(alice, unavailable, 1);
        UUID key = UUID.randomUUID();
        assertThat(checkout(alice, key).status()).isEqualTo(409);
        assertThat(stock(productId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_orders", Long.class)).isZero();
        assertThat(request("GET", "/api/cart", alice, null).body().get("items").size()).isEqualTo(2);
        adjust(unavailable, 1);
        assertThat(checkout(alice, key).status()).isEqualTo(201);
    }
    @Test void inactiveProductCannotBeCheckedOut() throws Exception {
        cart(alice, productId, 1); update(productId, "Keyboard", "10.10", false);
        assertThat(checkout(alice, UUID.randomUUID()).status()).isEqualTo(409);
        assertThat(request("GET", "/api/products", alice, null).body().get("totalElements").asLong()).isZero();
        assertThat(stock(productId)).isEqualTo(10);
    }
    @Test void concurrentBuyersCannotOversellLastItem() throws Exception {
        adjust(productId, -9); cart(alice, productId, 1); cart(bob, productId, 1);
        var results = race(List.of(() -> checkout(alice, UUID.randomUUID()), () -> checkout(bob, UUID.randomUUID())));
        assertThat(results.stream().map(Response::status).toList()).containsExactlyInAnyOrder(201, 409);
        assertThat(stock(productId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_orders", Long.class)).isEqualTo(1);
    }
    @Test void sixConcurrentRetriesCreateExactlyOneOrder() throws Exception {
        cart(alice, productId, 2); UUID key = UUID.randomUUID();
        List<Callable<Response>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) tasks.add(() -> checkout(alice, key));
        var results = race(tasks);
        assertThat(results.stream().filter(r -> r.status() == 201).count()).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.status() == 200).count()).isEqualTo(5);
        assertThat(results.stream().map(r -> r.body().get("id").asLong()).distinct().count()).isEqualTo(1);
        assertThat(stock(productId)).isEqualTo(8);
    }
    @Test void idempotencyKeysAreScopedToTheUser() throws Exception {
        cart(alice, productId, 1); cart(bob, productId, 1); UUID key = UUID.randomUUID();
        var a = checkout(alice, key); var b = checkout(bob, key);
        assertThat(a.status()).isEqualTo(201); assertThat(b.status()).isEqualTo(201);
        assertThat(a.body().get("id")).isNotEqualTo(b.body().get("id"));
    }
    @Test void concurrentCancellationRestoresInventoryExactlyOnce() throws Exception {
        cart(alice, productId, 3); long id = checkout(alice, UUID.randomUUID()).body().get("id").asLong();
        var results = race(List.of(() -> request("POST", "/api/orders/" + id + "/cancel", alice, null),
                () -> request("POST", "/api/orders/" + id + "/cancel", alice, null)));
        assertThat(results.stream().map(Response::status).toList()).containsOnly(200);
        assertThat(stock(productId)).isEqualTo(10);
        assertThat(request("POST", "/api/admin/orders/" + id + "/ship", admin, null).status()).isEqualTo(409);
    }
    @Test void shippingIsAdminOnlyAndBlocksCancellation() throws Exception {
        cart(alice, productId, 1); long id = checkout(alice, UUID.randomUUID()).body().get("id").asLong();
        assertThat(request("POST", "/api/admin/orders/" + id + "/ship", alice, null).status()).isEqualTo(403);
        assertThat(request("POST", "/api/admin/orders/" + id + "/ship", admin, null).body().get("status").asText()).isEqualTo("SHIPPED");
        assertThat(request("POST", "/api/admin/orders/" + id + "/ship", admin, null).status()).isEqualTo(200);
        assertThat(request("POST", "/api/orders/" + id + "/cancel", alice, null).status()).isEqualTo(409);
        assertThat(stock(productId)).isEqualTo(9);
    }
    @Test void orderDetailsAndCancellationEnforceOwnership() throws Exception {
        cart(alice, productId, 1); long id = checkout(alice, UUID.randomUUID()).body().get("id").asLong();
        assertThat(request("GET", "/api/orders/" + id, bob, null).status()).isEqualTo(404);
        assertThat(request("POST", "/api/orders/" + id + "/cancel", bob, null).status()).isEqualTo(404);
        assertThat(request("GET", "/api/orders/mine", bob, null).body().get("totalElements").asLong()).isZero();
        assertThat(request("GET", "/api/orders/" + id, admin, null).status()).isEqualTo(200);
        assertThat(request("POST", "/api/orders/" + id + "/cancel", admin, null).status()).isEqualTo(200);
    }
    @Test void emptyCartUnknownResourcesAndMissingKeyAreRejected() throws Exception {
        assertThat(checkout(alice, UUID.randomUUID()).status()).isEqualTo(409);
        assertThat(request("GET", "/api/products/9999999", alice, null).status()).isEqualTo(404);
        assertThat(request("PUT", "/api/cart/items/9999999", alice, Map.of("quantity", 1)).status()).isEqualTo(404);
        assertThat(request("GET", "/api/orders/9999999", alice, null).status()).isEqualTo(404);
        assertThat(request("POST", "/api/orders", alice, Map.of("shippingAddress", "Astana demo address")).status()).isEqualTo(400);
        assertThat(request("POST", "/api/orders", alice, Map.of("shippingAddress", "Astana demo address"), "not-a-uuid").status()).isEqualTo(400);
    }
    @Test void oppositeCartInsertionOrderStillAllowsConcurrentMultiProductCheckout() throws Exception {
        long other = product("SECOND", "Second", "2.50", 10);
        cart(alice, productId, 1); cart(alice, other, 2);
        cart(bob, other, 2); cart(bob, productId, 1);
        var results = race(List.of(() -> checkout(alice, UUID.randomUUID()), () -> checkout(bob, UUID.randomUUID())));
        assertThat(results.stream().map(Response::status).toList()).containsOnly(201);
        assertThat(stock(productId)).isEqualTo(8); assertThat(stock(other)).isEqualTo(6);
    }
    @Test void openApiAndHealthAreAvailable() throws Exception {
        var api = request("GET", "/v3/api-docs", null, null);
        assertThat(api.status()).isEqualTo(200);
        assertThat(api.body().get("paths").has("/api/orders")).isTrue();
        assertThat(api.raw()).contains("Idempotency-Key");
        assertThat(request("GET", "/actuator/health", null, null).status()).isEqualTo(200);
    }

    String seed(String email, AppUser.Role role) {
        return tokens.issue(users.saveAndFlush(new AppUser(email, "Test User", SEED_HASH, role))).accessToken();
    }
    Map<String, Object> productData(String sku, String name, String price, int stock) {
        return Map.of("sku", sku, "name", name, "price", new BigDecimal(price), "stock", stock);
    }
    long product(String sku, String name, String price, int stock) throws Exception {
        var response = request("POST", "/api/admin/products", admin, productData(sku, name, price, stock));
        assertThat(response.status()).as(response.raw()).isEqualTo(201);
        return response.body().get("id").asLong();
    }
    void cart(String token, long id, int quantity) throws Exception {
        var response = request("PUT", "/api/cart/items/" + id, token, Map.of("quantity", quantity));
        assertThat(response.status()).as(response.raw()).isEqualTo(204);
    }
    Response adjust(long id, int delta) throws Exception { return request("POST", "/api/admin/products/" + id + "/stock", admin, Map.of("delta", delta)); }
    Response update(long id, String name, String price, boolean active) throws Exception {
        return request("PUT", "/api/admin/products/" + id, admin, Map.of("name", name, "price", new BigDecimal(price), "active", active));
    }
    long stock(long id) throws Exception { return request("GET", "/api/products/" + id + "/stock", alice, null).body().get("available").asLong(); }
    Response checkout(String token, UUID key) throws Exception {
        return request("POST", "/api/orders", token, Map.of("shippingAddress", "Astana demo address"), key.toString());
    }
    Response request(String method, String path, String token, Object data) throws Exception { return request(method, path, token, data, null); }
    Response request(String method, String path, String token, Object data, String key) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (key != null) builder.header("Idempotency-Key", key);
        builder.header("Content-Type", "application/json").method(method, data == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(data)));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body().isBlank() ? json.nullNode() : json.readTree(response.body()), response.body(), response.headers());
    }
    List<Response> race(List<Callable<Response>> tasks) throws Exception {
        var pool = Executors.newFixedThreadPool(tasks.size());
        var ready = new CountDownLatch(tasks.size()); var start = new CountDownLatch(1);
        try {
            var futures = tasks.stream().map(task -> pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException(); return task.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var results = new ArrayList<Response>();
            for (var future : futures) results.add(future.get(25, TimeUnit.SECONDS));
            return results;
        } finally { start.countDown(); pool.shutdownNow(); }
    }
    record Response(int status, JsonNode body, String raw, HttpHeaders headers) {}
}
