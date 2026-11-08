package dev.domikhub.orderflow.cart;

import dev.domikhub.orderflow.common.ApiException;
import dev.domikhub.orderflow.product.*;
import dev.domikhub.orderflow.user.UserRepository;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CartService {
    private final CartRepository carts;
    private final ProductRepository products;
    private final UserRepository users;
    public CartService(CartRepository carts, ProductRepository products, UserRepository users) {
        this.carts = carts; this.products = products; this.users = users;
    }
    @Transactional(readOnly = true)
    public CartDtos.View get(Long userId) {
        var items = carts.findByUserIdOrderByProductId(userId);
        var catalogue = products.findAllById(items.stream().map(CartItem::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));
        var lines = items.stream().map(item -> {
            var p = catalogue.get(item.getProductId());
            return new CartDtos.Line(p.getId(), p.getName(), item.getQuantity(), p.getPrice(),
                p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())), p.isActive(), p.getStock());
        }).toList();
        return new CartDtos.View(lines, lines.stream().map(CartDtos.Line::subtotal)
                .reduce(new BigDecimal("0.00"), BigDecimal::add), "KZT");
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void set(Long userId, Long productId, int quantity) {
        lockUser(userId);
        var p = products.findById(productId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
        if (!p.isActive()) throw new ApiException(HttpStatus.CONFLICT, "Product is inactive");
        var existing = carts.findByUserIdAndProductId(userId, productId);
        if (existing.isPresent()) existing.get().setQuantity(quantity);
        else {
            if (carts.countByUserId(userId) >= 50) throw new ApiException(HttpStatus.CONFLICT, "Cart supports at most 50 different products");
            carts.save(new CartItem(userId, productId, quantity));
        }
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void remove(Long userId, Long productId) { lockUser(userId); carts.deleteByUserIdAndProductId(userId, productId); }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void clear(Long userId) { lockUser(userId); carts.deleteByUserId(userId); }
    private void lockUser(Long userId) {
        users.lockById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }
}
