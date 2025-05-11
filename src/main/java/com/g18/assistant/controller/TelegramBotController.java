package com.g18.assistant.controller;

import com.g18.assistant.dto.response.TelegramMessageResponse;
import com.g18.assistant.entity.TelegramMessage;
import com.g18.assistant.mapper.TelegramMessageMapper;
import com.g18.assistant.service.TelegramBotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shops/{shopId}/telegram")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Telegram Bot", description = "Manage Telegram bots for shops")
public class TelegramBotController {
    
    private final TelegramBotService telegramBotService;
    private final TelegramMessageMapper telegramMessageMapper;
    
    @PostMapping("/start")
    @Operation(summary = "Start Telegram bot", description = "Start a Telegram bot for a shop")
    public ResponseEntity<?> startBot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId) {
        
        try {
            String username = jwt.getSubject();
            boolean success = telegramBotService.startBot(shopId, username);
            
            if (success) {
                return ResponseEntity.ok(Map.of("status", "Bot started successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Failed to start bot. Make sure you have configured a valid Telegram token."));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error starting Telegram bot: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }
    
    @PostMapping("/stop")
    @Operation(summary = "Stop Telegram bot", description = "Stop a running Telegram bot for a shop")
    public ResponseEntity<?> stopBot(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId) {
        
        try {
            String username = jwt.getSubject();
            boolean success = telegramBotService.stopBot(shopId, username);
            
            if (success) {
                return ResponseEntity.ok(Map.of("status", "Bot stopped successfully"));
            } else {
                return ResponseEntity.ok(Map.of("status", "No bot was running"));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error stopping Telegram bot: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }
    
    @GetMapping("/status")
    @Operation(summary = "Get bot status", description = "Check if a Telegram bot is running for a shop")
    public ResponseEntity<?> getBotStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId) {
        
        try {
            String username = jwt.getSubject();
            boolean isRunning = telegramBotService.getBotStatus(shopId, username);
            
            return ResponseEntity.ok(Map.of("running", isRunning));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error checking Telegram bot status: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }
    
    @GetMapping("/messages")
    @Operation(summary = "Get recent messages", 
               description = "Get recent messages received by a shop's Telegram bot")
    public ResponseEntity<?> getRecentMessages(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "50") int limit) {
        
        try {
            String username = jwt.getSubject();
            List<TelegramMessage> messages = telegramBotService.getRecentMessages(shopId, username, limit);
            List<TelegramMessageResponse> responses = telegramMessageMapper.toResponseList(messages);
            
            return ResponseEntity.ok(responses);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error retrieving Telegram messages: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }
    
    @PostMapping("/send")
    @Operation(summary = "Send message", description = "Send a message to a Telegram chat via the shop's bot")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String username = jwt.getSubject();
            
            if (!request.containsKey("chatId") || !request.containsKey("message")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Both 'chatId' and 'message' are required"));
            }
            
            Long chatId = Long.parseLong(request.get("chatId").toString());
            String message = request.get("message").toString();
            
            boolean success = telegramBotService.sendMessage(shopId, username, chatId, message);
            
            if (success) {
                return ResponseEntity.ok(Map.of("status", "Message sent successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Failed to send message. Make sure the bot is running."));
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid chatId format. Must be a number."));
        } catch (Exception e) {
            log.error("Error sending Telegram message: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }
} 