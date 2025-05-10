package com.g18.assistant.service;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationHistoryService {
    void addMessage(Long shopId, String customerId, String role, String message);
    List<ConversationEntry> getRecentHistory(Long shopId, String customerId, int limit);
    void clearHistory(Long shopId, String customerId);

    class ConversationEntry {
        public String role; // "customer" hoặc "assistant"
        public String message;
        public LocalDateTime timestamp;
        
        public ConversationEntry(String role, String message) {
            this.role = role;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
        
        public ConversationEntry(String role, String message, LocalDateTime timestamp) {
            this.role = role;
            this.message = message;
            this.timestamp = timestamp;
        }
    }
} 