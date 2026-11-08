package dev.domikhub.orderflow.product;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisProductCacheTest {
    @Test void redisOutageReturnsCacheMissInsteadOfFailingCatalogueRead() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Offline"));
        var cache = new RedisProductCache(redis);
        assertThat(cache.get(1L)).isEmpty();
    }
    @Test void redisOutageDoesNotFailWritesOrEvictions() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Offline"));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("Offline"));
        var cache = new RedisProductCache(redis);
        assertThatCode(() -> cache.put(new ProductDtos.View(1L, "SKU", "Product", BigDecimal.ONE, "KZT", true))).doesNotThrowAnyException();
        assertThatCode(() -> cache.evict(1L)).doesNotThrowAnyException();
    }
}
