package com.g18.assistant.repository;

import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Order;
import com.g18.assistant.entity.Order.OrderStatus;
import com.g18.assistant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomer(Customer customer);
    List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);
    List<Order> findByProduct(Product product);
    
    @Query("SELECT o FROM Order o JOIN o.customer c WHERE c.shop.id = :shopId")
    List<Order> findAllByShopId(Long shopId);
    
    @Query("SELECT o FROM Order o JOIN o.customer c WHERE c.shop.id = :shopId AND o.status = :status")
    List<Order> findAllByShopIdAndStatus(Long shopId, OrderStatus status);
      @Query("SELECT o FROM Order o JOIN o.customer c WHERE c.shop.id = :shopId AND o.createdAt BETWEEN :startDate AND :endDate")
    List<Order> findAllByShopIdAndCreatedAtBetween(Long shopId, LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId AND o.product.id = :productId AND o.createdAt > :afterTime ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByCustomerAndProduct(Long customerId, Long productId, LocalDateTime afterTime);
}