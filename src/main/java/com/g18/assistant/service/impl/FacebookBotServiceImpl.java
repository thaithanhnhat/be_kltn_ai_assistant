package com.g18.assistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookMessageDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;
import com.g18.assistant.entity.FacebookAccessToken;
import com.g18.assistant.repository.FacebookAccessTokenRepository;
import com.g18.assistant.service.FacebookBotService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookBotServiceImpl implements FacebookBotService {

    private final FacebookAccessTokenRepository facebookAccessTokenRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${app.facebook.api.url:https://graph.facebook.com/v18.0}")
    private String facebookApiUrl;
    
    @Value("${app.base.url}")
    private String baseUrl;

    @Override
    public FacebookWebhookConfigDto configureWebhook(Long shopId) {
        // Generate a unique verify token
        String verifyToken = generateRandomToken();
        
        // Create webhook URL
        String webhookUrl = baseUrl + "/api/facebook/webhook/" + shopId;
        
        // Save or update the configuration
        Optional<FacebookAccessToken> existingToken = facebookAccessTokenRepository.findByShopId(shopId);
        
        FacebookAccessToken tokenEntity;
        if (existingToken.isPresent()) {
            tokenEntity = existingToken.get();
            tokenEntity.setVerifyToken(verifyToken);
            tokenEntity.setWebhookUrl(webhookUrl);
        } else {
            tokenEntity = FacebookAccessToken.builder()
                    .shopId(shopId)
                    .verifyToken(verifyToken)
                    .webhookUrl(webhookUrl)
                    .pageId("") // Empty default value for page_id
                    .active(false)
                    .accessToken("") // Will be updated later
                    .build();
        }
        
        facebookAccessTokenRepository.save(tokenEntity);
        
        return FacebookWebhookConfigDto.builder()
                .webhookUrl(webhookUrl)
                .verifyToken(verifyToken)
                .build();
    }

    @Override
    public boolean verifyWebhook(String verifyToken, String challenge) {
        Optional<FacebookAccessToken> tokenEntity = facebookAccessTokenRepository.findByVerifyToken(verifyToken);
        return tokenEntity.isPresent();
    }

    @Override
    public void saveAccessToken(Long shopId, String accessToken, String pageId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        tokenEntity.setAccessToken(accessToken);
        tokenEntity.setPageId(pageId);
        facebookAccessTokenRepository.save(tokenEntity);
    }

    @Override
    public void startBot(Long shopId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        if (tokenEntity.getAccessToken() == null || tokenEntity.getAccessToken().isEmpty()) {
            throw new IllegalStateException("Access token not configured for shop: " + shopId);
        }
        
        tokenEntity.setActive(true);
        facebookAccessTokenRepository.save(tokenEntity);
        
        // Subscribe to webhook events
        subscribeToWebhook(tokenEntity.getAccessToken());
    }

    @Override
    public void stopBot(Long shopId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        tokenEntity.setActive(false);
        facebookAccessTokenRepository.save(tokenEntity);
        
        // Unsubscribe from webhook events if needed
        // unsubscribeFromWebhook(tokenEntity.getAccessToken());
    }

    @Override
    public FacebookBotStatusDto getBotStatus(Long shopId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        return FacebookBotStatusDto.builder()
                .shopId(shopId)
                .active(tokenEntity.isActive())
                .webhookUrl(tokenEntity.getWebhookUrl())
                .build();
    }

    @Override
    public void handleIncomingMessage(String requestBody) {
        try {
            FacebookMessageDto messageDto = objectMapper.readValue(requestBody, FacebookMessageDto.class);
            
            if (messageDto.getObject() == null || !messageDto.getObject().equals("page")) {
                log.warn("Received non-page event: {}", messageDto.getObject());
                return;
            }
            
            if (messageDto.getEntries() == null || messageDto.getEntries().isEmpty()) {
                log.warn("No entries in the webhook event");
                return;
            }
            
            // Process each messaging event
            for (FacebookMessageDto.Entry entry : messageDto.getEntries()) {
                if (entry.getMessaging() == null || entry.getMessaging().isEmpty()) {
                    continue;
                }
                
                for (FacebookMessageDto.Messaging messaging : entry.getMessaging()) {
                    if (messaging.getMessage() == null || messaging.getMessage().getText() == null) {
                        continue;
                    }
                    
                    String senderId = messaging.getSender().getId();
                    String recipientId = messaging.getRecipient().getId();
                    String messageText = messaging.getMessage().getText();
                    
                    log.info("Received message from {}: {}", senderId, messageText);
                    
                    // Here you would call your AI service to generate a response
                    // For now, let's just echo the message back
                    String response = "Echo: " + messageText;
                    
                    // Extract shopId from recipientId or use another method to identify the shop
                    // For demo purposes, we'll assume the recipientId matches a page that belongs to a shop
                    // In a real implementation, you'd need to map the page ID to the shop ID
                    Long shopId = findShopIdByPageId(recipientId);
                    
                    if (shopId != null) {
                        sendMessage(shopId, senderId, response);
                    } else {
                        log.warn("Could not find shop for page ID: {}", recipientId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Facebook message: {}", e.getMessage(), e);
        }
    }
    
    // Helper method to find a shop ID from a Facebook page ID
    // In a real implementation, you would have a mapping table or service
    private Long findShopIdByPageId(String pageId) {
        // For demo purposes, we'll try to find a shop that has this page configured
        // In reality, you would have a more robust mapping mechanism
        Optional<FacebookAccessToken> tokenEntity = facebookAccessTokenRepository.findAll().stream()
                .filter(FacebookAccessToken::isActive)
                .findFirst();
                
        return tokenEntity.map(FacebookAccessToken::getShopId).orElse(null);
    }

    @Override
    public void sendMessage(Long shopId, String recipientId, String message) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopIdAndActive(shopId, true)
                .orElseThrow(() -> new EntityNotFoundException("Active Facebook configuration not found for shop: " + shopId));
        
        String url = facebookApiUrl + "/me/messages?access_token=" + tokenEntity.getAccessToken();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> recipientMap = new HashMap<>();
        recipientMap.put("id", recipientId);
        
        Map<String, Object> messageMap = new HashMap<>();
        messageMap.put("text", message);
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("recipient", recipientMap);
        requestBody.put("message", messageMap);
        
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Error sending message to Facebook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message to Facebook", e);
        }
    }
    
    private void subscribeToWebhook(String accessToken) {
        String url = facebookApiUrl + "/me/subscribed_apps?access_token=" + accessToken;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> entity = new HttpEntity<>("{}", headers);
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Error subscribing to Facebook webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to subscribe to Facebook webhook", e);
        }
    }
    
    private String generateRandomToken() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
} 