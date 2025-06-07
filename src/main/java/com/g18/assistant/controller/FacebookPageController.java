package com.g18.assistant.controller;

import com.g18.assistant.dto.FacebookWebhookConfigDto;
import com.g18.assistant.service.FacebookBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/facebook-pages")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Facebook Page Management", description = "APIs for managing Facebook fanpages for shops")
public class FacebookPageController {

    private final FacebookBotService facebookBotService;

    @PostMapping("/shops/{shopId}/pages")
    @Operation(summary = "Add a new Facebook fanpage to a shop", 
               description = "Configure a new Facebook fanpage for the specified shop")
    public ResponseEntity<Map<String, String>> addPageToShop(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @RequestBody FacebookWebhookConfigDto.CreateRequest request) {
        try {
            facebookBotService.savePageConfiguration(shopId, request);
            return ResponseEntity.ok(Map.of(
                "message", "Facebook page configured successfully",
                "pageId", request.getPageId(),
                "shopId", shopId.toString()
            ));
        } catch (Exception e) {
            log.error("Error adding page to shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to configure Facebook page",
                "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/shops/{shopId}/pages")
    @Operation(summary = "Get all Facebook fanpages for a shop",
               description = "Retrieve all configured Facebook fanpages for the specified shop")
    public ResponseEntity<List<FacebookWebhookConfigDto>> getShopPages(
            @Parameter(description = "Shop ID") @PathVariable Long shopId) {
        try {
            List<FacebookWebhookConfigDto> pages = facebookBotService.getShopPageConfigurations(shopId);
            return ResponseEntity.ok(pages);
        } catch (Exception e) {
            log.error("Error getting pages for shop {}: {}", shopId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/shops/{shopId}/pages/{pageId}")
    @Operation(summary = "Get specific Facebook fanpage configuration",
               description = "Retrieve configuration for a specific Facebook fanpage")
    public ResponseEntity<FacebookWebhookConfigDto> getPageConfiguration(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId) {
        try {
            FacebookWebhookConfigDto config = facebookBotService.getPageConfiguration(shopId, pageId);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            log.error("Error getting page configuration for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/shops/{shopId}/pages/{pageId}")
    @Operation(summary = "Update Facebook fanpage configuration",
               description = "Update configuration for a specific Facebook fanpage")
    public ResponseEntity<Map<String, String>> updatePageConfiguration(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId,
            @RequestBody FacebookWebhookConfigDto.UpdateRequest request) {
        try {
            facebookBotService.updatePageConfiguration(shopId, pageId, request);
            return ResponseEntity.ok(Map.of(
                "message", "Facebook page configuration updated successfully",
                "pageId", pageId,
                "shopId", shopId.toString()
            ));
        } catch (Exception e) {
            log.error("Error updating page configuration for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to update Facebook page configuration",
                "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/shops/{shopId}/pages/{pageId}")
    @Operation(summary = "Remove Facebook fanpage from shop",
               description = "Remove and unsubscribe a Facebook fanpage from the specified shop")
    public ResponseEntity<Map<String, String>> removePageFromShop(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId) {
        try {
            facebookBotService.deletePageConfiguration(shopId, pageId);
            return ResponseEntity.ok(Map.of(
                "message", "Facebook page removed successfully",
                "pageId", pageId,
                "shopId", shopId.toString()
            ));
        } catch (Exception e) {
            log.error("Error removing page from shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to remove Facebook page",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/shops/{shopId}/pages/{pageId}/subscribe")
    @Operation(summary = "Subscribe Facebook fanpage to webhook",
               description = "Subscribe a Facebook fanpage to receive messages via webhook")
    public ResponseEntity<Map<String, String>> subscribePageToWebhook(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId) {
        try {
            facebookBotService.subscribePageToWebhook(shopId, pageId);
            return ResponseEntity.ok(Map.of(
                "message", "Facebook page subscribed to webhook successfully",
                "pageId", pageId,
                "shopId", shopId.toString()
            ));
        } catch (Exception e) {
            log.error("Error subscribing page to webhook for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to subscribe page to webhook",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/shops/{shopId}/pages/{pageId}/unsubscribe")
    @Operation(summary = "Unsubscribe Facebook fanpage from webhook",
               description = "Unsubscribe a Facebook fanpage from receiving messages via webhook")
    public ResponseEntity<Map<String, String>> unsubscribePageFromWebhook(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId) {
        try {
            facebookBotService.unsubscribePageFromWebhook(shopId, pageId);
            return ResponseEntity.ok(Map.of(
                "message", "Facebook page unsubscribed from webhook successfully",
                "pageId", pageId,
                "shopId", shopId.toString()
            ));
        } catch (Exception e) {
            log.error("Error unsubscribing page from webhook for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to unsubscribe page from webhook",
                "message", e.getMessage()
            ));
        }
    }

    @PostMapping("/shops/{shopId}/pages/{pageId}/test-message")
    @Operation(summary = "Send test message to Facebook fanpage",
               description = "Send a test message to verify Facebook fanpage configuration")
    public ResponseEntity<Map<String, String>> sendTestMessage(
            @Parameter(description = "Shop ID") @PathVariable Long shopId,
            @Parameter(description = "Facebook Page ID") @PathVariable String pageId,
            @RequestBody Map<String, String> request) {
        try {
            String testUserId = request.get("testUserId");
            String message = request.getOrDefault("message", "Hello! This is a test message from your AI assistant.");
            
            if (testUserId == null || testUserId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Test user ID is required",
                    "message", "Please provide a testUserId in the request body"
                ));
            }
            
            facebookBotService.sendMessage(shopId, testUserId, message);
            return ResponseEntity.ok(Map.of(
                "message", "Test message sent successfully",
                "pageId", pageId,
                "shopId", shopId.toString(),
                "recipientId", testUserId
            ));
        } catch (Exception e) {
            log.error("Error sending test message for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to send test message",
                "message", e.getMessage()
            ));
        }
    }
}
