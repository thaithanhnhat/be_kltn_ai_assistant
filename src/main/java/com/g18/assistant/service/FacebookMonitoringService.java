package com.g18.assistant.service;

import com.g18.assistant.dto.FacebookMessageDto;

public interface FacebookMonitoringService {
    
    /**
     * Log incoming Facebook message
     */
    void logIncomingMessage(Long shopId, String pageId, String senderId, String recipientId, 
                          String messageText, String aiIntent, Double aiConfidence, Long processingTimeMs);
    
    /**
     * Log outgoing Facebook message
     */
    void logOutgoingMessage(Long shopId, String pageId, String senderId, String recipientId, 
                          String messageText, boolean success, String errorMessage);
    
    /**
     * Log webhook event
     */
    void logWebhookEvent(String pageId, String eventType, String eventData);
    
    /**
     * Mark webhook event as processed
     */
    void markWebhookEventProcessed(Long eventId, boolean success, String errorMessage);
    
    /**
     * Get message statistics for a shop
     */
    FacebookMessageStats getMessageStats(Long shopId, String startDate, String endDate);
    
    /**
     * Get page performance metrics
     */
    FacebookPageMetrics getPageMetrics(Long shopId, String pageId, String startDate, String endDate);
    
    /**
     * Get webhook health status
     */
    FacebookWebhookHealth getWebhookHealth(String pageId);
    
    // DTOs for statistics and metrics
    interface FacebookMessageStats {
        Long getTotalIncoming();
        Long getTotalOutgoing();
        Long getSuccessfulMessages();
        Long getFailedMessages();
        Double getAverageResponseTime();
        String getMostCommonIntent();
    }
    
    interface FacebookPageMetrics {
        String getPageId();
        String getPageName();
        Long getUniqueUsers();
        Long getTotalMessages();
        Double getAverageResponseTime();
        Double getSuccessRate();
    }
    
    interface FacebookWebhookHealth {
        String getPageId();
        Boolean getIsHealthy();
        Long getLastEventTimestamp();
        Long getFailedEventsLast24h();
        String getStatus();
    }
}
