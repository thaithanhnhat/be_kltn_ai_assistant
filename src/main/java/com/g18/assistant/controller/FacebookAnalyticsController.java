package com.g18.assistant.controller;

import com.g18.assistant.service.FacebookMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/facebook-analytics")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Facebook Analytics", description = "APIs for Facebook integration monitoring and analytics")
public class FacebookAnalyticsController {

    private final FacebookMonitoringService facebookMonitoringService;

    @GetMapping("/shops/{shopId}/message-stats")
    @Operation(summary = "Get message statistics for a shop",
               description = "Retrieve Facebook message statistics for the specified shop within a date range")
    public ResponseEntity<FacebookMonitoringService.FacebookMessageStats> getMessageStats(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Start date (YYYY-MM-DD)") 
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(7)}") 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") 
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}") 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            FacebookMonitoringService.FacebookMessageStats stats = 
                    facebookMonitoringService.getMessageStats(shopId, startDateStr, endDateStr);
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting message stats for shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/shops/{shopId}/pages/{pageId}/metrics")
    @Operation(summary = "Get performance metrics for a specific Facebook page",
               description = "Retrieve detailed performance metrics for a Facebook page")
    public ResponseEntity<FacebookMonitoringService.FacebookPageMetrics> getPageMetrics(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId,
            @Parameter(description = "Start date (YYYY-MM-DD)") 
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now().minusDays(7)}") 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (YYYY-MM-DD)") 
            @RequestParam(defaultValue = "#{T(java.time.LocalDate).now()}") 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        try {
            String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            FacebookMonitoringService.FacebookPageMetrics metrics = 
                    facebookMonitoringService.getPageMetrics(shopId, pageId, startDateStr, endDateStr);
            
            return ResponseEntity.ok(metrics);
        } catch (Exception e) {
            log.error("Error getting page metrics for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/pages/{pageId}/webhook-health")
    @Operation(summary = "Get webhook health status for a Facebook page",
               description = "Check the health status of webhook integration for a specific Facebook page")
    public ResponseEntity<FacebookMonitoringService.FacebookWebhookHealth> getWebhookHealth(
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId) {
        try {
            FacebookMonitoringService.FacebookWebhookHealth health = 
                    facebookMonitoringService.getWebhookHealth(pageId);
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            log.error("Error getting webhook health for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/shops/{shopId}/dashboard")
    @Operation(summary = "Get comprehensive dashboard data for a shop",
               description = "Retrieve all Facebook integration data for dashboard display")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Days back to analyze") 
            @RequestParam(defaultValue = "7") int daysBack) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(daysBack);
            
            String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            
            // Get overall statistics
            FacebookMonitoringService.FacebookMessageStats stats = 
                    facebookMonitoringService.getMessageStats(shopId, startDateStr, endDateStr);
            
            // Prepare dashboard response
            Map<String, Object> dashboard = Map.of(
                "shopId", shopId,
                "period", Map.of(
                    "startDate", startDateStr,
                    "endDate", endDateStr,
                    "daysBack", daysBack
                ),
                "messageStats", Map.of(
                    "totalIncoming", stats.getTotalIncoming(),
                    "totalOutgoing", stats.getTotalOutgoing(),
                    "successfulMessages", stats.getSuccessfulMessages(),
                    "failedMessages", stats.getFailedMessages(),
                    "averageResponseTime", stats.getAverageResponseTime(),
                    "mostCommonIntent", stats.getMostCommonIntent()
                ),
                "summary", Map.of(
                    "successRate", stats.getTotalOutgoing() > 0 ? 
                            (stats.getSuccessfulMessages() * 100.0 / stats.getTotalOutgoing()) : 0.0,
                    "totalInteractions", stats.getTotalIncoming() + stats.getTotalOutgoing(),
                    "averageResponseTimeMs", stats.getAverageResponseTime()
                )
            );
            
            return ResponseEntity.ok(dashboard);
            
        } catch (Exception e) {
            log.error("Error getting dashboard data for shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to get dashboard data",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/pages/{pageId}/webhook-test")
    @Operation(summary = "Test webhook connectivity for a Facebook page",
               description = "Send a test webhook event to verify connectivity")
    public ResponseEntity<Map<String, Object>> testWebhook(
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId,
            @RequestBody(required = false) Map<String, Object> testData) {
        try {
            // Log a test webhook event
            String eventData = testData != null ? testData.toString() : "{}";
            facebookMonitoringService.logWebhookEvent(pageId, "webhook_test", eventData);
            
            // Get current health status
            FacebookMonitoringService.FacebookWebhookHealth health = 
                    facebookMonitoringService.getWebhookHealth(pageId);
            
            return ResponseEntity.ok(Map.of(
                "message", "Webhook test completed successfully",
                "pageId", pageId,
                "health", Map.of(
                    "isHealthy", health.getIsHealthy(),
                    "status", health.getStatus(),
                    "lastEventTimestamp", health.getLastEventTimestamp(),
                    "failedEventsLast24h", health.getFailedEventsLast24h()
                )
            ));
            
        } catch (Exception e) {
            log.error("Error testing webhook for page {}: {}", pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to test webhook",
                "message", e.getMessage()
            ));
        }
    }
}
