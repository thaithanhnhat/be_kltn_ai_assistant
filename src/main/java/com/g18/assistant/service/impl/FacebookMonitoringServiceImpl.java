package com.g18.assistant.service.impl;

import com.g18.assistant.service.FacebookMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookMonitoringServiceImpl implements FacebookMonitoringService {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.facebook.monitoring.log-messages:true}")
    private boolean logMessages;

    @Value("${app.facebook.monitoring.log-webhook-events:true}")
    private boolean logWebhookEvents;

    @Value("${app.facebook.monitoring.track-response-time:true}")
    private boolean trackResponseTime;

    @Override
    public void logIncomingMessage(Long shopId, String pageId, String senderId, String recipientId,
                                 String messageText, String aiIntent, Double aiConfidence, Long processingTimeMs) {
        if (!logMessages) return;

        try {
            String sql = """
                INSERT INTO facebook_message_logs 
                (shop_id, page_id, sender_id, recipient_id, message_text, message_type, 
                 ai_intent, ai_confidence, processing_time_ms, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'incoming', ?, ?, ?, 'success', NOW())
                """;

            jdbcTemplate.update(sql, shopId, pageId, senderId, recipientId, messageText,
                    aiIntent, aiConfidence, processingTimeMs);

            log.debug("Logged incoming Facebook message from {} to shop {}", senderId, shopId);

        } catch (Exception e) {
            log.error("Failed to log incoming Facebook message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logOutgoingMessage(Long shopId, String pageId, String senderId, String recipientId,
                                 String messageText, boolean success, String errorMessage) {
        if (!logMessages) return;

        try {
            String sql = """
                INSERT INTO facebook_message_logs 
                (shop_id, page_id, sender_id, recipient_id, message_text, message_type, 
                 status, error_message, created_at)
                VALUES (?, ?, ?, ?, ?, 'outgoing', ?, ?, NOW())
                """;

            jdbcTemplate.update(sql, shopId, pageId, senderId, recipientId, messageText,
                    success ? "success" : "failed", errorMessage);

            log.debug("Logged outgoing Facebook message to {} from shop {}", recipientId, shopId);

        } catch (Exception e) {
            log.error("Failed to log outgoing Facebook message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void logWebhookEvent(String pageId, String eventType, String eventData) {
        if (!logWebhookEvents) return;

        try {
            String sql = """
                INSERT INTO facebook_webhook_events 
                (page_id, event_type, event_data, received_at)
                VALUES (?, ?, CAST(? AS JSON), NOW())
                """;

            jdbcTemplate.update(sql, pageId, eventType, eventData);

            log.debug("Logged Facebook webhook event {} for page {}", eventType, pageId);

        } catch (Exception e) {
            log.error("Failed to log Facebook webhook event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void markWebhookEventProcessed(Long eventId, boolean success, String errorMessage) {
        try {
            String sql = """
                UPDATE facebook_webhook_events 
                SET processed = ?, processing_error = ?, processed_at = NOW()
                WHERE id = ?
                """;

            jdbcTemplate.update(sql, success, errorMessage, eventId);

        } catch (Exception e) {
            log.error("Failed to mark webhook event as processed: {}", e.getMessage(), e);
        }
    }

    @Override
    public FacebookMessageStats getMessageStats(Long shopId, String startDate, String endDate) {
        try {
            String sql = """
                SELECT 
                    SUM(CASE WHEN message_type = 'incoming' THEN 1 ELSE 0 END) as total_incoming,
                    SUM(CASE WHEN message_type = 'outgoing' THEN 1 ELSE 0 END) as total_outgoing,
                    SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as successful_messages,
                    SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed_messages,
                    AVG(processing_time_ms) as avg_response_time,
                    (SELECT ai_intent FROM facebook_message_logs fml2 
                     WHERE fml2.shop_id = ? AND fml2.ai_intent IS NOT NULL 
                     AND fml2.created_at BETWEEN ? AND ?
                     GROUP BY ai_intent ORDER BY COUNT(*) DESC LIMIT 1) as most_common_intent
                FROM facebook_message_logs 
                WHERE shop_id = ? AND created_at BETWEEN ? AND ?
                """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, 
                    shopId, startDate, endDate, shopId, startDate, endDate);

            return new FacebookMessageStatsImpl(result);

        } catch (Exception e) {
            log.error("Failed to get message stats for shop {}: {}", shopId, e.getMessage(), e);
            return new FacebookMessageStatsImpl(Map.of());
        }
    }

    @Override
    public FacebookPageMetrics getPageMetrics(Long shopId, String pageId, String startDate, String endDate) {
        try {
            String sql = """
                SELECT 
                    page_id,
                    (SELECT page_name FROM facebook_access_tokens WHERE page_id = ?) as page_name,
                    COUNT(DISTINCT sender_id) as unique_users,
                    COUNT(*) as total_messages,
                    AVG(processing_time_ms) as avg_response_time,
                    (SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END) * 100.0 / COUNT(*)) as success_rate
                FROM facebook_message_logs 
                WHERE shop_id = ? AND page_id = ? AND created_at BETWEEN ? AND ?
                GROUP BY page_id
                """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, pageId, shopId, pageId, startDate, endDate);

            return new FacebookPageMetricsImpl(result);

        } catch (Exception e) {
            log.error("Failed to get page metrics for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return new FacebookPageMetricsImpl(Map.of());
        }
    }

    @Override
    public FacebookWebhookHealth getWebhookHealth(String pageId) {
        try {
            String sql = """
                SELECT 
                    page_id,
                    MAX(received_at) as last_event_timestamp,
                    SUM(CASE WHEN processed = FALSE AND received_at > DATE_SUB(NOW(), INTERVAL 24 HOUR) THEN 1 ELSE 0 END) as failed_events_24h
                FROM facebook_webhook_events 
                WHERE page_id = ?
                GROUP BY page_id
                """;

            Map<String, Object> result = jdbcTemplate.queryForMap(sql, pageId);

            return new FacebookWebhookHealthImpl(result);

        } catch (Exception e) {
            log.error("Failed to get webhook health for page {}: {}", pageId, e.getMessage(), e);
            return new FacebookWebhookHealthImpl(Map.of("pageId", pageId, "isHealthy", false));
        }
    }

    // Implementation classes for DTOs
    private static class FacebookMessageStatsImpl implements FacebookMessageStats {
        private final Map<String, Object> data;

        public FacebookMessageStatsImpl(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public Long getTotalIncoming() {
            return ((Number) data.getOrDefault("total_incoming", 0L)).longValue();
        }

        @Override
        public Long getTotalOutgoing() {
            return ((Number) data.getOrDefault("total_outgoing", 0L)).longValue();
        }

        @Override
        public Long getSuccessfulMessages() {
            return ((Number) data.getOrDefault("successful_messages", 0L)).longValue();
        }

        @Override
        public Long getFailedMessages() {
            return ((Number) data.getOrDefault("failed_messages", 0L)).longValue();
        }

        @Override
        public Double getAverageResponseTime() {
            return ((Number) data.getOrDefault("avg_response_time", 0.0)).doubleValue();
        }

        @Override
        public String getMostCommonIntent() {
            return (String) data.get("most_common_intent");
        }
    }

    private static class FacebookPageMetricsImpl implements FacebookPageMetrics {
        private final Map<String, Object> data;

        public FacebookPageMetricsImpl(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public String getPageId() {
            return (String) data.get("page_id");
        }

        @Override
        public String getPageName() {
            return (String) data.get("page_name");
        }

        @Override
        public Long getUniqueUsers() {
            return ((Number) data.getOrDefault("unique_users", 0L)).longValue();
        }

        @Override
        public Long getTotalMessages() {
            return ((Number) data.getOrDefault("total_messages", 0L)).longValue();
        }

        @Override
        public Double getAverageResponseTime() {
            return ((Number) data.getOrDefault("avg_response_time", 0.0)).doubleValue();
        }

        @Override
        public Double getSuccessRate() {
            return ((Number) data.getOrDefault("success_rate", 0.0)).doubleValue();
        }
    }

    private static class FacebookWebhookHealthImpl implements FacebookWebhookHealth {
        private final Map<String, Object> data;

        public FacebookWebhookHealthImpl(Map<String, Object> data) {
            this.data = data;
        }

        @Override
        public String getPageId() {
            return (String) data.get("pageId");
        }

        @Override
        public Boolean getIsHealthy() {
            Long failedEvents = ((Number) data.getOrDefault("failed_events_24h", 0L)).longValue();
            return failedEvents < 10; // Consider unhealthy if more than 10 failed events in 24h
        }

        @Override
        public Long getLastEventTimestamp() {
            Object timestamp = data.get("last_event_timestamp");
            return timestamp != null ? ((java.sql.Timestamp) timestamp).getTime() : 0L;
        }

        @Override
        public Long getFailedEventsLast24h() {
            return ((Number) data.getOrDefault("failed_events_24h", 0L)).longValue();
        }

        @Override
        public String getStatus() {
            return getIsHealthy() ? "healthy" : "unhealthy";
        }
    }
}
