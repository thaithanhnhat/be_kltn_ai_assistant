package com.g18.assistant.service;

import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.entity.Customer;

import java.util.Map;
import java.util.Optional;

/**
 * Service for managing pending orders that require additional customer information
 * to be completed (e.g., missing address information).
 * 
 * This service provides a centralized way to handle pending orders across
 * different communication channels (AI chat, Telegram bot, etc.)
 */
public interface PendingOrderService {
    
    /**
     * Data class to hold pending order information
     */
    class PendingOrderInfo {
        private Long customerId;
        private Long productId;
        private Integer quantity;
        private String note;
        private OrderSource source;
        private java.time.LocalDateTime createdAt;
        
        public PendingOrderInfo(Long customerId, Long productId, Integer quantity, OrderSource source) {
            this.customerId = customerId;
            this.productId = productId;
            this.quantity = quantity;
            this.source = source;
            this.createdAt = java.time.LocalDateTime.now();
        }
        
        public Long getCustomerId() {
            return customerId;
        }
        
        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }
        
        public Long getProductId() {
            return productId;
        }
        
        public void setProductId(Long productId) {
            this.productId = productId;
        }
        
        public Integer getQuantity() {
            return quantity;
        }
        
        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }
        
        public String getNote() {
            return note;
        }
        
        public void setNote(String note) {
            this.note = note;
        }
        
        public OrderSource getSource() {
            return source;
        }
        
        public void setSource(OrderSource source) {
            this.source = source;
        }
        
        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }
        
        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }
    
    /**
     * Enum to track where the pending order came from
     */
    enum OrderSource {
        AI_CHAT,
        TELEGRAM_BOT,
        WEB_INTERFACE
    }
    
    /**
     * Store a pending order for later processing
     */
    void storePendingOrder(String customerKey, Long customerId, Long productId, Integer quantity, OrderSource source);
    
    /**
     * Store a pending order with note
     */
    void storePendingOrder(String customerKey, Long customerId, Long productId, Integer quantity, String note, OrderSource source);
    
    /**
     * Retrieve a pending order
     */
    PendingOrderInfo getPendingOrder(String customerKey);
    
    /**
     * Remove a pending order
     */
    void removePendingOrder(String customerKey);
    
    /**
     * Check if a pending order exists
     */
    boolean hasPendingOrder(String customerKey);
    
    /**
     * Get all pending orders (for monitoring/debugging)
     */
    java.util.Map<String, PendingOrderInfo> getAllPendingOrders();
    
    /**
     * Clear all pending orders (for cleanup)
     */
    void clearAllPendingOrders();
}