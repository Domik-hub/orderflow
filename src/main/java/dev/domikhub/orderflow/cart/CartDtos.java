package dev.domikhub.orderflow.cart;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {
    private CartDtos() {}
    public record Quantity(@Min(1) @Max(99) int quantity) {}
    public record Line(Long productId, String name, int quantity, BigDecimal unitPrice, BigDecimal subtotal,
                       boolean active, long availableStock) {}
    public record View(List<Line> items, BigDecimal estimatedTotal, String currency) {}
}
