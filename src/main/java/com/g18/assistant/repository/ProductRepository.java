package com.g18.assistant.repository;

import com.g18.assistant.entity.Product;
import com.g18.assistant.entity.Shop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    List<Product> findByShopIdAndActiveTrue(Long shopId);
    
    Page<Product> findByShopIdAndActiveTrue(Long shopId, Pageable pageable);
    
    /**
     * Find a product by ID and shop ID
     * 
     * @param id The product ID
     * @param shopId The shop ID
     * @return The product if found
     */
    Optional<Product> findByIdAndShopId(Long id, Long shopId);
    
    /**
     * Find all products belonging to a shop
     * 
     * @param shopId The shop ID
     * @param pageable Pagination information
     * @return Page of products
     */
    Page<Product> findByShopId(Long shopId, Pageable pageable);
    
    @Query("SELECT p FROM Product p WHERE p.shop.id = :shopId AND p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.category) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND p.active = true")
    Page<Product> searchProducts(Long shopId, String keyword, Pageable pageable);
    
    @Query("SELECT p FROM Product p WHERE p.active = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Product> searchAllActiveProducts(String keyword, Pageable pageable);
    
    Page<Product> findByCategoryAndActiveTrue(String category, Pageable pageable);
    
    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.shop.id = :shopId AND p.active = true")
    List<String> findAllCategoriesByShopId(Long shopId);
} 