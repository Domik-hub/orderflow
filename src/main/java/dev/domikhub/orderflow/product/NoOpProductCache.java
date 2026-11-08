package dev.domikhub.orderflow.product;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.cache-enabled", havingValue = "false", matchIfMissing = true)
public class NoOpProductCache implements ProductCache {
    @Override public Optional<ProductDtos.View> get(Long id) { return Optional.empty(); }
    @Override public void put(ProductDtos.View product) {}
    @Override public void evict(Long id) {}
}
