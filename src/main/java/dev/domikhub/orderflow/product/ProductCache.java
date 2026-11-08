package dev.domikhub.orderflow.product;

import java.util.Optional;

public interface ProductCache {
    Optional<ProductDtos.View> get(Long id);
    void put(ProductDtos.View product);
    void evict(Long id);
}
