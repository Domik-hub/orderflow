package dev.domikhub.orderflow.order;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OrderRepository extends JpaRepository<ShopOrder, Long> {
    Optional<ShopOrder> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
    Page<ShopOrder> findByUserId(Long userId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.id = :id")
    Optional<ShopOrder> lockById(Long id);
}
