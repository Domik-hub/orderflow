package dev.domikhub.orderflow.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByProductId(Long userId);
    Optional<CartItem> findByUserIdAndProductId(Long userId, Long productId);
    long countByUserId(Long userId);
    void deleteByUserIdAndProductId(Long userId, Long productId);
    void deleteByUserId(Long userId);
}
