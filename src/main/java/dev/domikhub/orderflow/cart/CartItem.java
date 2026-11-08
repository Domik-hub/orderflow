package dev.domikhub.orderflow.cart;

import jakarta.persistence.*;

@Entity @Table(name = "cart_items")
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(nullable = false) private int quantity;
    protected CartItem() {}
    public CartItem(Long userId, Long productId, int quantity) { this.userId = userId; this.productId = productId; this.quantity = quantity; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
