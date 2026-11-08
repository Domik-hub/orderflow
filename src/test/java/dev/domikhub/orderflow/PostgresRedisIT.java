package dev.domikhub.orderflow;

import dev.domikhub.orderflow.product.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class PostgresRedisIT extends ApiContract {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @Autowired StringRedisTemplate cache;
    @DynamicPropertySource static void settings(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.data.redis.host", redis::getHost);
        properties.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        properties.add("app.cache-enabled", () -> true);
        properties.add("management.health.redis.enabled", () -> true);
    }
    @Test void redisCachesProductJsonWithExpiryAndInvalidatesAfterUpdate() throws Exception {
        String key = RedisProductCache.key(productId);
        assertThat(cache.hasKey(key)).isFalse();
        assertThat(request("GET", "/api/products/" + productId, alice, null).status()).isEqualTo(200);
        assertThat(cache.opsForValue().get(key)).contains("Keyboard");
        assertThat(cache.getExpire(key)).isBetween(1L, 60L);
        var marker = new ProductDtos.View(productId, "KEYBOARD", "Cached name", new BigDecimal("10.10"), "KZT", true);
        cache.opsForValue().set(key, json.writeValueAsString(marker), Duration.ofSeconds(60));
        assertThat(request("GET", "/api/products/" + productId, alice, null).body().get("name").asText()).isEqualTo("Cached name");
        update(productId, "New name", "11.25", true);
        assertThat(cache.hasKey(key)).isFalse();
        assertThat(request("GET", "/api/products/" + productId, alice, null).body().get("price").decimalValue()).isEqualByComparingTo("11.25");
    }
    @Test void staleCacheNeverControlsCheckoutPriceOrStock() throws Exception {
        var stale = new ProductDtos.View(productId, "KEYBOARD", "Stale", new BigDecimal("0.01"), "KZT", true);
        cache.opsForValue().set(RedisProductCache.key(productId), json.writeValueAsString(stale), Duration.ofSeconds(60));
        cart(alice, productId, 2);
        var order = checkout(alice, UUID.randomUUID());
        assertThat(order.status()).isEqualTo(201);
        assertThat(order.body().get("total").decimalValue()).isEqualByComparingTo("20.20");
        assertThat(stock(productId)).isEqualTo(8);
    }
    @Test void malformedCacheEntryFallsBackToDatabase() throws Exception {
        cache.opsForValue().set(RedisProductCache.key(productId), "not-json", Duration.ofSeconds(60));
        assertThat(request("GET", "/api/products/" + productId, alice, null).body().get("name").asText()).isEqualTo("Keyboard");
    }
}
