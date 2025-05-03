package com.g18.assistant.repository;

import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {
    
    List<Shop> findByUser(User user);
    
    List<Shop> findByUserAndStatus(User user, Shop.ShopStatus status);
    
    Optional<Shop> findByIdAndUser(Long id, User user);
    
    boolean existsByNameAndUser(String name, User user);
    
    boolean existsByIdAndUser(Long id, User user);
} 