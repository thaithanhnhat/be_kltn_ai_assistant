package com.g18.assistant.repository;

import com.g18.assistant.entity.FacebookMessageLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FacebookMessageLogRepository extends JpaRepository<FacebookMessageLog, Long> {
    
    // Find messages by shop ID
    List<FacebookMessageLog> findByShopIdOrderByCreatedAtDesc(Long shopId);
    
    // Find messages by page ID
    List<FacebookMessageLog> findByPageIdOrderByCreatedAtDesc(String pageId);
    
    // Find messages by sender ID
    List<FacebookMessageLog> findBySenderIdOrderByCreatedAtDesc(String senderId);
    
    // Find messages by status
    List<FacebookMessageLog> findByStatusOrderByCreatedAtDesc(String status);
    
    // Find messages within date range
    List<FacebookMessageLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime startDate, LocalDateTime endDate);
    
    // Find messages by shop and date range
    @Query("SELECT f FROM FacebookMessageLog f WHERE f.shopId = :shopId " +
           "AND f.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY f.createdAt DESC")
    List<FacebookMessageLog> findByShopIdAndDateRange(
        @Param("shopId") Long shopId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
    
    // Find paginated messages by shop ID
    Page<FacebookMessageLog> findByShopIdOrderByCreatedAtDesc(Long shopId, Pageable pageable);
    
    // Count messages by status
    long countByStatus(String status);
    
    // Count messages by shop ID and status
    long countByShopIdAndStatus(Long shopId, String status);
    
    // Find latest message by sender
    Optional<FacebookMessageLog> findFirstBySenderIdOrderByCreatedAtDesc(String senderId);
    
    // Get message statistics by shop
    @Query("SELECT COUNT(*) FROM FacebookMessageLog f WHERE f.shopId = :shopId")
    long countMessagesByShop(@Param("shopId") Long shopId);
    
    // Get error rate by shop
    @Query("SELECT COUNT(*) FROM FacebookMessageLog f WHERE f.shopId = :shopId AND f.status = 'ERROR'")
    long countErrorsByShop(@Param("shopId") Long shopId);
    
    // Get average processing time by shop
    @Query("SELECT AVG(f.processingTimeMs) FROM FacebookMessageLog f WHERE f.shopId = :shopId AND f.processingTimeMs IS NOT NULL")
    Double getAverageProcessingTime(@Param("shopId") Long shopId);
}