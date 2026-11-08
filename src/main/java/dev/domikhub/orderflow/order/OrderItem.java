package dev.domikhub.orderflow.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import dev.domikhub.orderflow.product.Product;

@Entity @Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "order_id") private ShopOrder order;
    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(nullable = false, length = 40) private String sku;
    @Column(name = "product_name", nullable = false, length = 120) private String productName;
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false) private int quantity;
    protected OrderItem() {}
    public OrderItem(ShopOrder order, Product product, int quantity) {
        this.order = order; this.productId = product.getId(); this.sku = product.getSku();
        this.productName = product.getName(); this.unitPrice = product.getPrice(); this.quantity = quantity;
    }
    public Long getProductId() { return productId; }
    public String getSku() { return sku; }
    public String getProductName() { return productName; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
}
