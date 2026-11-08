package dev.domikhub.orderflow.order;

import dev.domikhub.orderflow.cart.*;
import dev.domikhub.orderflow.common.ApiException;
import dev.domikhub.orderflow.product.*;
import dev.domikhub.orderflow.user.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final CartRepository carts;
    private final ProductRepository products;
    private final UserRepository users;
    private final Clock clock;
    public OrderService(OrderRepository orders, CartRepository carts, ProductRepository products, UserRepository users, Clock clock) {
        this.orders = orders; this.carts = carts; this.products = products; this.users = users; this.clock = clock;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDtos.Result checkout(Long userId, UUID key, OrderDtos.Checkout input) {
        // The user row serializes their cart writes and checkouts, including retries across app instances.
        users.lockById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
        String address = input.shippingAddress().strip();
        var previous = orders.findByUserIdAndIdempotencyKey(userId, key.toString());
        if (previous.isPresent()) {
            if (!previous.get().getShippingAddress().equals(address))
                throw new ApiException(HttpStatus.CONFLICT, "Idempotency key already used with a different address");
            return new OrderDtos.Result(OrderDtos.View.of(previous.get()), true);
        }
        var cart = carts.findByUserIdOrderByProductId(userId);
        if (cart.isEmpty()) throw new ApiException(HttpStatus.CONFLICT, "Cart is empty");
        var order = new ShopOrder(userId, key.toString(), address, clock.instant());
        // Products are locked in ascending ID order to prevent opposite-order deadlocks.
        // Cart rows contain only product IDs, so products are first loaded AFTER acquiring their lock.
        for (var item : cart) {
            var product = products.lockById(item.getProductId()).orElseThrow();
            if (!product.isActive()) throw new ApiException(HttpStatus.CONFLICT, "A cart product is inactive");
            if (product.getStock() < item.getQuantity()) throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock for " + product.getSku());
            product.changeStock(-item.getQuantity());
            order.add(product, item.getQuantity());
        }
        orders.saveAndFlush(order);
        carts.deleteAll(cart);
        return new OrderDtos.Result(OrderDtos.View.of(order), false);
    }
    @Transactional(readOnly = true)
    public Page<OrderDtos.Summary> mine(Long userId, int page, int size) {
        return orders.findByUserId(userId, PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(OrderDtos.Summary::of);
    }
    @Transactional(readOnly = true)
    public OrderDtos.View get(AppUser user, Long id) {
        var order = orders.findById(id).orElseThrow(this::notFound);
        checkOwner(user, order);
        return OrderDtos.View.of(order);
    }
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderDtos.View cancel(AppUser user, Long id) {
        var order = orders.lockById(id).orElseThrow(this::notFound);
        checkOwner(user, order);
        if (order.getStatus() == ShopOrder.Status.CANCELLED) return OrderDtos.View.of(order);
        if (order.getStatus() != ShopOrder.Status.PLACED) throw new ApiException(HttpStatus.CONFLICT, "Shipped orders cannot be cancelled");
        for (var item : order.getItems().stream().sorted(Comparator.comparing(OrderItem::getProductId)).toList()) {
            products.lockById(item.getProductId()).orElseThrow().changeStock(item.getQuantity());
        }
        order.setStatus(ShopOrder.Status.CANCELLED);
        return OrderDtos.View.of(order);
    }
    @Transactional
    public OrderDtos.View ship(Long id) {
        var order = orders.lockById(id).orElseThrow(this::notFound);
        if (order.getStatus() == ShopOrder.Status.CANCELLED) throw new ApiException(HttpStatus.CONFLICT, "Cancelled orders cannot be shipped");
        order.setStatus(ShopOrder.Status.SHIPPED);
        return OrderDtos.View.of(order);
    }
    private void checkOwner(AppUser user, ShopOrder order) {
        if (!order.getUserId().equals(user.getId()) && user.getRole() != AppUser.Role.ADMIN) throw notFound();
    }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, "Order not found"); }
}
