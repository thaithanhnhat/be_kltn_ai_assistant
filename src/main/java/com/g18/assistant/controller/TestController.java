package com.g18.assistant.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Health check endpoint called at {}", LocalDateTime.now());
        
        return ResponseEntity.ok(Map.of(
            "status", "OK",
            "timestamp", LocalDateTime.now().toString(),
            "service", "Facebook Bot Service",
            "message", "Application is running successfully"
        ));
    }
    
    @GetMapping("/webhook-test")
    public ResponseEntity<String> webhookTest() {
        log.info("Webhook test endpoint called at {}", LocalDateTime.now());
        return ResponseEntity.ok("Webhook endpoint is accessible");
    }
}
