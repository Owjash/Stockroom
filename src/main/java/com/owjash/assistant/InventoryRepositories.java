package com.owjash.assistant;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

interface Suppliers extends JpaRepository<Supplier,Long> {}
interface Products extends JpaRepository<Product,Long> {
    Optional<Product> findBySku(String sku);
    boolean existsBySupplierId(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id=:id")
    Optional<Product> lockById(@Param("id") Long id);
    @Query("select p from Product p where p.quantity <= p.reorderLevel order by p.quantity, p.id")
    List<Product> lowStock();
}
interface StockOrders extends JpaRepository<StockOrder,Long> {
    List<StockOrder> findTop100ByOrderByCreatedAtDesc();
    List<StockOrder> findByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(String type,Instant since);
}
