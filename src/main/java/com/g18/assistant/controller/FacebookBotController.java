package com.g18.assistant.controller;

import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;
import com.g18.assistant.dto.FacebookWebhookListDto;
import com.g18.assistant.service.FacebookBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facebook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Facebook Bot", description = "Manage Facebook Messenger bots for shops")
public class FacebookBotController {

    private final FacebookBotService facebookBotService;

    @PostMapping("/shops/{shopId}/configure")
    @Operation(summary = "Configure Facebook webhook", description = "Configure a Facebook webhook for a shop")
    public ResponseEntity<FacebookWebhookConfigDto> configureWebhook(@PathVariable Long shopId) {
        FacebookWebhookConfigDto config = facebookBotService.configureWebhook(shopId);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/shops/{shopId}/access-token")
    @Operation(summary = "Save Facebook access token", description = "Save Facebook page access token for a shop")
    public ResponseEntity<Void> saveAccessToken(
            @PathVariable Long shopId, 
            @RequestParam String accessToken,
            @RequestParam String pageId) {
        facebookBotService.saveAccessToken(shopId, accessToken, pageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shops/{shopId}/start")
    @Operation(summary = "Start Facebook bot", description = "Start Facebook Messenger bot for a shop")
    public ResponseEntity<Void> startBot(@PathVariable Long shopId) {
        facebookBotService.startBot(shopId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shops/{shopId}/stop")
    @Operation(summary = "Stop Facebook bot", description = "Stop Facebook Messenger bot for a shop")
    public ResponseEntity<Void> stopBot(@PathVariable Long shopId) {
        facebookBotService.stopBot(shopId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/shops/{shopId}/status")
    @Operation(summary = "Get Facebook bot status", description = "Get the status of Facebook Messenger bot for a shop")
    public ResponseEntity<FacebookBotStatusDto> getBotStatus(@PathVariable Long shopId) {
        FacebookBotStatusDto status = facebookBotService.getBotStatus(shopId);
        return ResponseEntity.ok(status);
    }    /**
     * Webhook verification endpoint for Facebook
     * This endpoint is called by Facebook when you set up the webhook
     */
    @GetMapping("/webhook/{shopId}")
    @Operation(summary = "Verify Facebook webhook", description = "Webhook verification endpoint called by Facebook")
    public ResponseEntity<?> verifyWebhook(
            @PathVariable Long shopId,
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        
        log.info("Facebook webhook verification request - shopId: {}, mode: {}, verifyToken: {}, challenge: {}", 
                shopId, mode, verifyToken, challenge);
        
        try {
            if ("subscribe".equals(mode)) {
                boolean isValid = facebookBotService.verifyWebhook(verifyToken, challenge);
                log.info("Webhook verification result for shop {}: {}", shopId, isValid);
                
                if (isValid) {
                    log.info("Returning challenge: {}", challenge);
                    return ResponseEntity.ok(challenge);
                } else {
                    log.warn("Invalid verify token for shop {}: {}", shopId, verifyToken);
                }
            } else {
                log.warn("Invalid mode for webhook verification: {}", mode);
            }
        } catch (Exception e) {
            log.error("Error during webhook verification: {}", e.getMessage(), e);
        }
        
        return ResponseEntity.badRequest().build();
    }

    /**
     * Webhook endpoint that receives messages from Facebook
     */
    @PostMapping("/webhook/{shopId}")
    @Operation(summary = "Receive Facebook messages", description = "Webhook endpoint that receives messages from Facebook Messenger")
    public ResponseEntity<Void> receiveMessage(@PathVariable Long shopId, @RequestBody String requestBody) {
        facebookBotService.handleIncomingMessage(requestBody);
        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint to manually send a message to a Facebook user
     */
    @PostMapping("/shops/{shopId}/send")
    @Operation(summary = "Send Facebook message", description = "Manually send a message to a Facebook user")
    public ResponseEntity<Void> sendMessage(
            @PathVariable Long shopId,
            @RequestParam String recipientId,
            @RequestParam String message) {
        
        facebookBotService.sendMessage(shopId, recipientId, message);
        return ResponseEntity.ok().build();
    }
}