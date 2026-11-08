package dev.domikhub.orderflow.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import dev.domikhub.orderflow.product.Product;

@Entity @Table(name = "shop_orders")
public class ShopOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "idempotency_key", nullable = false, length = 36) private String idempotencyKey;
    @Column(name = "shipping_address", nullable = false, length = 300) private String shippingAddress;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal total;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL) @OrderBy("productId ASC")
    private List<OrderItem> items = new ArrayList<>();
    protected ShopOrder() {}
    public ShopOrder(Long userId, String key, String address, Instant createdAt) {
        this.userId = userId; this.idempotencyKey = key; this.shippingAddress = address;
        this.createdAt = createdAt; this.status = Status.PLACED; this.total = new BigDecimal("0.00"); this.currency = "KZT";
    }
    public void add(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
        total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getShippingAddress() { return shippingAddress; }
    public Status getStatus() { return status; }
    public BigDecimal getTotal() { return total; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
    public void setStatus(Status status) { this.status = status; }
    public enum Status { PLACED, SHIPPED, CANCELLED }
}
