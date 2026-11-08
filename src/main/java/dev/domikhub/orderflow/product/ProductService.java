package dev.domikhub.orderflow.product;

import dev.domikhub.orderflow.common.ApiException;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;

@Service
public class ProductService {
    private final ProductRepository products;
    private final ProductCache cache;
    public ProductService(ProductRepository products, ProductCache cache) { this.products = products; this.cache = cache; }
    @Transactional(readOnly = true)
    public Page<ProductDtos.View> list(String query, BigDecimal min, BigDecimal max, int page, int size, String sort) {
        if (min.compareTo(max) > 0) throw new ApiException(HttpStatus.BAD_REQUEST, "minPrice must not exceed maxPrice");
        var ordering = switch (sort) {
            case "priceAsc" -> Sort.by("price", "id");
            case "priceDesc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
            default -> Sort.by("id");
        };
        return products.search(query.strip().toLowerCase(Locale.ROOT), min, max, PageRequest.of(page, size, ordering))
                .map(ProductDtos.View::of);
    }
    @Transactional(readOnly = true)
    public ProductDtos.View get(Long id) {
        return cache.get(id).orElseGet(() -> {
            var view = ProductDtos.View.of(find(id));
            cache.put(view);
            return view;
        });
    }
    @Transactional(readOnly = true)
    public ProductDtos.Stock stock(Long id) { return new ProductDtos.Stock(id, find(id).getStock()); }
    @Transactional
    public ProductDtos.View create(ProductDtos.Create input) {
        return ProductDtos.View.of(products.saveAndFlush(new Product(input.sku(), input.name().strip(), input.price(), input.stock())));
    }
    @Transactional
    public ProductDtos.View update(Long id, ProductDtos.Update input) {
        var product = lock(id);
        product.update(input.name().strip(), input.price(), input.active());
        // Invalidation before commit could allow another request to cache the old database state.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { cache.evict(id); }
        });
        return ProductDtos.View.of(product);
    }
    @Transactional
    public ProductDtos.Stock adjust(Long id, long delta) {
        var product = lock(id);
        if (delta == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Stock adjustment must be nonzero");
        if (product.getStock() + delta < 0) throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
        product.changeStock(delta);
        return new ProductDtos.Stock(id, product.getStock());
    }
    private Product find(Long id) { return products.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found")); }
    private Product lock(Long id) { return products.lockById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found")); }
}
