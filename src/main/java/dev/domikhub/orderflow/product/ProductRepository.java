package dev.domikhub.orderflow.product;

import java.math.BigDecimal;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> lockById(Long id);
    @Query("select p from Product p where p.active = true and lower(p.name) like concat('%', :query, '%') " +
           "and p.price >= :minPrice and p.price <= :maxPrice")
    Page<Product> search(String query, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
}
