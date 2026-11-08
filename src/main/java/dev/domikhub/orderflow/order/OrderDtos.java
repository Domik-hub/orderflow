package dev.domikhub.orderflow.order;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}
    public record Checkout(@NotBlank @Size(min = 5, max = 300) String shippingAddress) {}
    public record Line(Long productId, String sku, String name, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
        static Line of(OrderItem item) { return new Line(item.getProductId(), item.getSku(), item.getProductName(),
            item.getUnitPrice(), item.getQuantity(), item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))); }
    }
    public record Summary(Long id, String status, BigDecimal total, String currency, Instant createdAt) {
        public static Summary of(ShopOrder o) { return new Summary(o.getId(), o.getStatus().name(), o.getTotal(), o.getCurrency(), o.getCreatedAt()); }
    }
    public record View(Long id, String status, BigDecimal total, String currency, Instant createdAt, String shippingAddress, List<Line> items) {
        public static View of(ShopOrder o) { return new View(o.getId(), o.getStatus().name(), o.getTotal(), o.getCurrency(),
            o.getCreatedAt(), o.getShippingAddress(), o.getItems().stream().map(Line::of).toList()); }
    }
    public record Result(View order, boolean replayed) {}
}
