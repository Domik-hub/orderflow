package dev.domikhub.orderflow.product;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String sku;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(nullable = false) private long stock;
    @Column(nullable = false) private boolean active;
    protected Product() {}
    public Product(String sku, String name, BigDecimal price, long stock) {
        this.sku = sku; this.name = name; this.price = price; this.stock = stock; this.active = true;
    }
    public Long getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public long getStock() { return stock; }
    public boolean isActive() { return active; }
    public void update(String name, BigDecimal price, boolean active) {
        this.name = name; this.price = price; this.active = active;
    }
    public void changeStock(long delta) { stock = Math.addExact(stock, delta); }
}
