package com.g18.assistant.repository;

import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Feedback;
import com.g18.assistant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByCustomer(Customer customer);
    List<Feedback> findByProduct(Product product);
    
    @Query("SELECT f FROM Feedback f JOIN f.product p WHERE p.shop.id = :shopId")
    List<Feedback> findAllByShopId(Long shopId);
    
    @Query("SELECT f FROM Feedback f JOIN f.product p WHERE p.id = :productId ORDER BY f.time DESC")
    List<Feedback> findByProductIdOrderByTimeDesc(Long productId);
} 