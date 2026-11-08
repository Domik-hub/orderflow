package dev.domikhub.orderflow.product;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.cache-enabled", havingValue = "true")
public class RedisProductCache implements ProductCache {
    public static final Duration TTL = Duration.ofSeconds(60);
    private static final Logger log = LoggerFactory.getLogger(RedisProductCache.class);
    private final StringRedisTemplate redis;
    private final JsonMapper json = JsonMapper.builder().build();
    public RedisProductCache(StringRedisTemplate redis) { this.redis = redis; }
    public static String key(Long id) { return "orderflow:product:v1:" + id; }
    @Override public Optional<ProductDtos.View> get(Long id) {
        try {
            String value = redis.opsForValue().get(key(id));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, ProductDtos.View.class));
        } catch (RuntimeException ex) {
            log.debug("Product cache read unavailable; using PostgreSQL", ex);
            return Optional.empty();
        }
    }
    @Override public void put(ProductDtos.View product) {
        try { redis.opsForValue().set(key(product.id()), json.writeValueAsString(product), TTL); }
        catch (RuntimeException ex) { log.debug("Product cache write unavailable", ex); }
    }
    @Override public void evict(Long id) {
        try { redis.delete(key(id)); }
        catch (RuntimeException ex) { log.debug("Product cache eviction unavailable; entry expires via TTL", ex); }
    }
}
