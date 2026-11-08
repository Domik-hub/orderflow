package dev.domikhub.orderflow.product;

import dev.domikhub.orderflow.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class ProductController {
    private final ProductService products;
    public ProductController(ProductService products) { this.products = products; }
    @GetMapping("/api/products")
    public PageResult<ProductDtos.View> list(@RequestParam(defaultValue = "") @Size(max = 100) String query,
        @RequestParam(defaultValue = "0") @DecimalMin("0") BigDecimal minPrice,
        @RequestParam(defaultValue = "9999999.99") @DecimalMin("0") BigDecimal maxPrice,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "id") @Pattern(regexp = "id|priceAsc|priceDesc") String sort) {
        return PageResult.of(products.list(query, minPrice, maxPrice, page, size, sort));
    }
    @GetMapping("/api/products/{id}") public ProductDtos.View get(@PathVariable Long id) { return products.get(id); }
    @GetMapping("/api/products/{id}/stock") public ProductDtos.Stock stock(@PathVariable Long id) { return products.stock(id); }
    @PostMapping("/api/admin/products") @ResponseStatus(HttpStatus.CREATED)
    public ProductDtos.View create(@Valid @RequestBody ProductDtos.Create input) { return products.create(input); }
    @PutMapping("/api/admin/products/{id}")
    public ProductDtos.View update(@PathVariable Long id, @Valid @RequestBody ProductDtos.Update input) { return products.update(id, input); }
    @PostMapping("/api/admin/products/{id}/stock")
    public ProductDtos.Stock adjust(@PathVariable Long id, @Valid @RequestBody ProductDtos.Adjustment input) { return products.adjust(id, input.delta()); }
}
