package dev.domikhub.orderflow.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {}
    public record Create(@NotBlank @Pattern(regexp = "[A-Z0-9-]{2,40}") String sku,
        @NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 7, fraction = 2) BigDecimal price,
        @Min(0) @Max(1000000) long stock) {}
    public record Update(@NotBlank @Size(max = 120) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 7, fraction = 2) BigDecimal price,
        @NotNull Boolean active) {}
    public record Adjustment(@Min(-1000000) @Max(1000000) long delta) {}
    // Stock is deliberately excluded from Redis-cached metadata.
    public record View(Long id, String sku, String name, BigDecimal price, String currency, boolean active) {
        public static View of(Product p) { return new View(p.getId(), p.getSku(), p.getName(), p.getPrice(), "KZT", p.isActive()); }
    }
    public record Stock(Long productId, long available) {}
}
