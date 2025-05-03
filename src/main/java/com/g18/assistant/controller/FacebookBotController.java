package com.g18.assistant.controller;

import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;
import com.g18.assistant.service.FacebookBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facebook")
@RequiredArgsConstructor
@Slf4j
public class FacebookBotController {

    private final FacebookBotService facebookBotService;

    @PostMapping("/shops/{shopId}/configure")
    public ResponseEntity<FacebookWebhookConfigDto> configureWebhook(@PathVariable Long shopId) {
        FacebookWebhookConfigDto config = facebookBotService.configureWebhook(shopId);
        return ResponseEntity.ok(config);
    }

    @PostMapping("/shops/{shopId}/access-token")
    public ResponseEntity<Void> saveAccessToken(
            @PathVariable Long shopId, 
            @RequestParam String accessToken,
            @RequestParam String pageId) {
        facebookBotService.saveAccessToken(shopId, accessToken, pageId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shops/{shopId}/start")
    public ResponseEntity<Void> startBot(@PathVariable Long shopId) {
        facebookBotService.startBot(shopId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/shops/{shopId}/stop")
    public ResponseEntity<Void> stopBot(@PathVariable Long shopId) {
        facebookBotService.stopBot(shopId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/shops/{shopId}/status")
    public ResponseEntity<FacebookBotStatusDto> getBotStatus(@PathVariable Long shopId) {
        FacebookBotStatusDto status = facebookBotService.getBotStatus(shopId);
        return ResponseEntity.ok(status);
    }

    /**
     * Webhook verification endpoint for Facebook
     * This endpoint is called by Facebook when you set up the webhook
     */
    @GetMapping("/webhook/{shopId}")
    public ResponseEntity<?> verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String verifyToken,
            @RequestParam("hub.challenge") String challenge) {
        
        if ("subscribe".equals(mode) && facebookBotService.verifyWebhook(verifyToken, challenge)) {
            return ResponseEntity.ok(challenge);
        }
        
        return ResponseEntity.badRequest().build();
    }

    /**
     * Webhook endpoint that receives messages from Facebook
     */
    @PostMapping("/webhook/{shopId}")
    public ResponseEntity<Void> receiveMessage(@PathVariable Long shopId, @RequestBody String requestBody) {
        facebookBotService.handleIncomingMessage(requestBody);
        return ResponseEntity.ok().build();
    }

    /**
     * Endpoint to manually send a message to a Facebook user
     */
    @PostMapping("/shops/{shopId}/send")
    public ResponseEntity<Void> sendMessage(
            @PathVariable Long shopId,
            @RequestParam String recipientId,
            @RequestParam String message) {
        
        facebookBotService.sendMessage(shopId, recipientId, message);
        return ResponseEntity.ok().build();
    }
} 