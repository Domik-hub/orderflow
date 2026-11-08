package dev.domikhub.orderflow.order;

import dev.domikhub.orderflow.auth.CurrentUser;
import dev.domikhub.orderflow.common.PageResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderController {
    private final OrderService orders;
    private final CurrentUser current;
    public OrderController(OrderService orders, CurrentUser current) { this.orders = orders; this.current = current; }
    @PostMapping("/api/orders")
    public ResponseEntity<OrderDtos.View> checkout(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody OrderDtos.Checkout input) {
        var result = orders.checkout(current.require(jwt).getId(), key, input);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/api/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.order());
    }
    @GetMapping("/api/orders/mine")
    public PageResult<OrderDtos.Summary> mine(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return PageResult.of(orders.mine(current.require(jwt).getId(), page, size));
    }
    @GetMapping("/api/orders/{id}")
    public OrderDtos.View get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return orders.get(current.require(jwt), id); }
    @PostMapping("/api/orders/{id}/cancel")
    public OrderDtos.View cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) { return orders.cancel(current.require(jwt), id); }
    @PostMapping("/api/admin/orders/{id}/ship")
    public OrderDtos.View ship(@PathVariable Long id) { return orders.ship(id); }
}
