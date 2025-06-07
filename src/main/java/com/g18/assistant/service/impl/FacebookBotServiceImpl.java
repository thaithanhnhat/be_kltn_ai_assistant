package com.g18.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookMessageDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;
import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.FacebookAccessToken;
import com.g18.assistant.entity.Product;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.FacebookAccessTokenRepository;
import com.g18.assistant.service.FacebookBotService;
import com.g18.assistant.service.FacebookMonitoringService;
import com.g18.assistant.service.OrderService;
import com.g18.assistant.service.PendingOrderService;
import com.g18.assistant.service.ShopAIService;
import com.g18.assistant.service.ShopService;
import com.g18.assistant.service.impl.FacebookPageValidationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookBotServiceImpl implements FacebookBotService {    private final FacebookAccessTokenRepository facebookAccessTokenRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ShopAIService shopAIService;
    private final ShopService shopService;    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final PendingOrderService pendingOrderService;
    private final FacebookPageValidationService facebookPageValidationService;
    
    // Optional monitoring service - may not be available in all environments
    @Autowired(required = false)
    private FacebookMonitoringService facebookMonitoringService;

    @Value("${app.facebook.api.url:https://graph.facebook.com/v18.0}")
    private String facebookApiUrl;
    
    @Value("${app.base.url}")
    private String baseUrl;
    
    @Value("${app.ngrok.url:}")
    private String ngrokUrl;    @Override
    public FacebookWebhookConfigDto configureWebhook(Long shopId) {
        // Generate a unique verify token
        String verifyToken = generateRandomToken();
        
        // Create webhook URL - use ngrok URL if available, otherwise fall back to baseUrl
        String webhookBaseUrl = (ngrokUrl != null && !ngrokUrl.trim().isEmpty()) ? ngrokUrl : baseUrl;
        String webhookUrl = webhookBaseUrl + "/assistant/api/facebook/webhook/" + shopId;
        
        log.info("Configuring Facebook webhook for shop {} with URL: {}", shopId, webhookUrl);
        
        // Save or update the configuration
        Optional<FacebookAccessToken> existingToken = facebookAccessTokenRepository.findByShopId(shopId);
        
        FacebookAccessToken tokenEntity;
        if (existingToken.isPresent()) {
            tokenEntity = existingToken.get();
            tokenEntity.setVerifyToken(verifyToken);
            tokenEntity.setWebhookUrl(webhookUrl);
            log.info("Updated existing Facebook webhook configuration for shop {}", shopId);
        } else {
            tokenEntity = FacebookAccessToken.builder()
                    .shopId(shopId)
                    .verifyToken(verifyToken)
                    .webhookUrl(webhookUrl)
                    .pageId("") // Empty default value for page_id
                    .active(false)
                    .accessToken("") // Will be updated later
                    .build();
            log.info("Created new Facebook webhook configuration for shop {}", shopId);
        }
        
        facebookAccessTokenRepository.save(tokenEntity);
        
        return FacebookWebhookConfigDto.builder()
                .webhookUrl(webhookUrl)
                .verifyToken(verifyToken)
                .pageId(tokenEntity.getPageId())
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
    }    @Override
    public FacebookBotStatusDto getBotStatus(Long shopId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        return FacebookBotStatusDto.builder()
                .shopId(shopId)
                .active(tokenEntity.isActive())
                .webhookUrl(tokenEntity.getWebhookUrl())
                .pageId(tokenEntity.getPageId())
                .hasAccessToken(tokenEntity.getAccessToken() != null && !tokenEntity.getAccessToken().isEmpty())
                .verifyToken(tokenEntity.getVerifyToken())
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
                    
                    log.info("Received Facebook message from {}: {}", senderId, messageText);
                    
                    // Get the shop ID from page ID
                    Long shopId = findShopIdByPageId(recipientId);
                    
                    if (shopId != null) {
                        // Check if this is an address command
                        if (messageText.startsWith("/address ")) {
                            String address = messageText.substring(9).trim(); // Extract address after "/address "
                            if (!address.isEmpty()) {
                                // Process address update
                                processAddressUpdate(shopId, senderId, address);
                                continue; // Skip AI processing for address commands
                            }
                        }
                          // Process message with AI service
                        try {
                            long startTime = System.currentTimeMillis();
                            
                            // Call our AI service to get a response
                            String aiResponse = shopAIService.processCustomerMessage(
                                    shopId, senderId, "Facebook User", messageText);
                            
                            long processingTime = System.currentTimeMillis() - startTime;
                            
                            // Parse the AI response
                            JsonNode responseJson = objectMapper.readTree(aiResponse);
                            
                            // Extract intent and confidence for monitoring
                            String detectedIntent = responseJson.has("detected_intent") ? 
                                    responseJson.get("detected_intent").asText() : null;
                            Double confidence = responseJson.has("confidence") ? 
                                    responseJson.get("confidence").asDouble() : null;
                              // Log incoming message if monitoring is available
                            try {
                                if (facebookMonitoringService != null) {
                                    facebookMonitoringService.logIncomingMessage(shopId, recipientId, senderId, recipientId, 
                                            messageText, detectedIntent, confidence, processingTime);
                                }
                            } catch (Exception monitoringException) {
                                log.debug("Monitoring service unavailable: {}", monitoringException.getMessage());
                            }
                            
                            // Check if there was an error
                            if (responseJson.has("error") && responseJson.get("error").asBoolean()) {
                                log.error("AI error: {}", responseJson.get("message").asText());
                                sendMessage(shopId, senderId, "I'm sorry, I'm having trouble understanding your request right now. Please try again later.");
                                return;
                            }
                            
                            // Extract the human-readable response text
                            String responseText = responseJson.has("response_text") ? 
                                    responseJson.get("response_text").asText() : 
                                    "Thank you for your message. I'll get back to you soon.";
                            
                            // Send the response to the user
                            sendMessage(shopId, senderId, responseText);
                            
                            // Log detected intent for monitoring
                            if (responseJson.has("detected_intent")) {
                                String intent = responseJson.get("detected_intent").asText();
                                log.info("AI detected intent for Shop {}, User {}: {}", 
                                        shopId, senderId, intent);
                            }
                            
                            // Handle any actions that need to be performed
                            if (responseJson.has("action_required") && responseJson.get("action_required").asBoolean()) {
                                log.info("AI indicates action required for Shop {}, User {}", 
                                        shopId, senderId);
                                
                                JsonNode actionDetails = responseJson.path("action_details");
                                
                                // Handle SHOWPRODUCT action type
                                if (actionDetails.has("action_type") && 
                                    "SHOWPRODUCT".equals(actionDetails.get("action_type").asText()) && 
                                    actionDetails.has("product_id")) {
                                    
                                    try {
                                        Long productId = actionDetails.get("product_id").asLong();
                                        sendProductDetails(shopId, senderId, productId);
                                    } catch (Exception e) {
                                        log.error("Error showing product details: {}", e.getMessage(), e);
                                    }
                                }
                                
                                // Handle PLACEORDER action type
                                if (actionDetails.has("action_type") && 
                                    "PLACEORDER".equals(actionDetails.get("action_type").asText())) {
                                    
                                    try {
                                        // Get order details from AI response
                                        Long productId = null;
                                        Integer quantity = null;
                                        String note = null;
                                        
                                        if (actionDetails.has("product_id")) {
                                            productId = actionDetails.get("product_id").asLong();
                                        }
                                        
                                        if (actionDetails.has("quantity")) {
                                            quantity = actionDetails.get("quantity").asInt();
                                        }
                                        
                                        if (actionDetails.has("note")) {
                                            note = actionDetails.get("note").asText();
                                        }
                                        
                                        if (productId != null && quantity != null) {
                                            processPlaceOrder(shopId, senderId, productId, quantity, note);
                                        }
                                    } catch (Exception e) {
                                        log.error("Error processing place order: {}", e.getMessage(), e);
                                    }
                                }
                            }
                            
                        } catch (Exception e) {
                            log.error("Error processing message with AI: {}", e.getMessage(), e);
                            sendMessage(shopId, senderId, "I'm sorry, I couldn't process your request. Please try again later.");
                        }
                    } else {
                        log.warn("Could not find shop for page ID: {}", recipientId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing Facebook message: {}", e.getMessage(), e);
        }
    }    /**
     * Send detailed product information to a Facebook user
     */
    private void sendProductDetails(Long shopId, String recipientId, Long productId) {
        try {
            // For now, send a generic product message since we don't have direct product access
            // In a real implementation, you'd want a proper product service method
            String productMessage = String.format(
                "🛍️ Product Information (ID: %d)\n\n" +
                "For detailed product information, please visit our shop or contact us directly.\n\n" +
                "Would you like to place an order for this product?", 
                productId
            );
            
            sendMessage(shopId, recipientId, productMessage);
            log.info("Sent product details for product {} to Facebook user {}", productId, recipientId);
            
        } catch (Exception e) {
            log.error("Error sending product details to Facebook user {}: {}", recipientId, e.getMessage(), e);
            sendMessage(shopId, recipientId, "I'm sorry, I couldn't retrieve the product details.");
        }
    }

    /**
     * Process order placement for Facebook user
     */
    private void processPlaceOrder(Long shopId, String senderId, Long productId, Integer quantity, String note) {
        try {
            // Generate email pattern for Facebook user
            String customerEmail = "facebook_" + senderId + "@example.com";
            
            // Find or create customer
            Customer customer = customerRepository.findByEmailAndShopId(customerEmail, shopId)
                .orElse(null);
                
            if (customer == null) {
                log.warn("Customer not found for Facebook user {} in shop {}. Creating new customer.", senderId, shopId);                // Create new customer
                customer = Customer.builder()                        .phone(senderId)
                        .shop(shopService.getShopByIdForBotServices(shopId))
                        .fullname("Facebook User #" + senderId)
                        .email(customerEmail)
                        .address("") // Will be updated when provided
                        .build();
                customer = customerRepository.save(customer);
                log.info("Created new customer for Facebook user: {}", senderId);
            }
              // Get product information from service, not direct entity access
            // Using the shopAIService to get product details
            Product product = null;
            try {
                // Try to get product through AI service which handles shop validation
                String productQuery = "product " + productId;
                String aiResponse = shopAIService.processCustomerMessage(shopId, "system", "System", productQuery);
                JsonNode responseJson = objectMapper.readTree(aiResponse);
                
                // For now, we'll use a simpler approach - assume product exists if AI can process it
                // In a real implementation, you'd want a proper product service method
                product = new Product(); // Placeholder - you'd get actual product data
                product.setName("Product #" + productId);
                product.setPrice(BigDecimal.valueOf(100000.0)); // Placeholder price
            } catch (Exception e) {
                log.warn("Could not get product details for product {}: {}", productId, e.getMessage());
            }
            
            if (product == null) {
                sendMessage(shopId, senderId, "Sorry, I couldn't find that product with ID: " + productId);
                return;
            }
            
            // Check if customer has address
            if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {            // Store pending order with the correct parameter order
            pendingOrderService.storePendingOrder(senderId, customer.getId(), productId, quantity, PendingOrderService.OrderSource.AI_CHAT);
                
                String message = String.format(
                    "📦 I'd be happy to place this order for you!\n\n" +
                    "🛍️ Product: %s\n" +
                    "🔢 Quantity: %d\n" +
                    "💰 Total: %s VND\n\n" +
                    "📍 To complete your order, please provide your delivery address by typing:\n" +
                    "/address [Your full address]",                    product.getName(),
                    quantity,
                    String.format("%,.0f", product.getPrice().multiply(BigDecimal.valueOf(quantity)))
                );
                
                sendMessage(shopId, senderId, message);
                return;
            }
            
            // Check for recent duplicate orders
            java.time.LocalDateTime recentTime = java.time.LocalDateTime.now().minusSeconds(30);
            List<OrderDTO> recentOrders = orderService.findRecentOrdersByCustomerAndProduct(
                customer.getId(), productId, recentTime);
            
            if (!recentOrders.isEmpty()) {
                log.info("Found recent order for customer {} and product {}, preventing duplicate creation", 
                    customer.getId(), productId);
                
                OrderDTO existingOrder = recentOrders.get(0);
                String confirmationMessage = String.format(
                    "✅ Your order has been processed successfully!\n\n" +
                    "🔢 Order ID: #%d\n" +
                    "🛍️ Product: %s\n" +
                    "🔢 Quantity: %d\n" +
                    "🏷️ Status: %s\n\n" +
                    "📦 Delivery address: %s\n\n" +
                    "Thank you for your order!",
                    existingOrder.getId(),
                    product.getName(),
                    existingOrder.getQuantity(),
                    existingOrder.getStatus(),
                    customer.getAddress()
                );
                
                sendMessage(shopId, senderId, confirmationMessage);
                return;
            }
              // Create the order using proper builder pattern
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .customerId(customer.getId())
                    .productId(productId)
                    .quantity(quantity)
                    .note(note)
                    .build();
            
            OrderDTO createdOrder = orderService.createOrder(orderRequest);
            
            // Send confirmation
            String confirmationMessage = String.format(
                "✅ Order created successfully!\n\n" +
                "🔢 Order ID: #%d\n" +
                "🛍️ Product: %s\n" +
                "🔢 Quantity: %d\n" +
                "💰 Total: %s VND\n" +
                "📦 Delivery address: %s\n\n" +
                "Thank you for your order! We'll process it soon.",
                createdOrder.getId(),
                product.getName(),                createdOrder.getQuantity(),
                String.format("%,.0f", product.getPrice().multiply(BigDecimal.valueOf(quantity))),
                customer.getAddress()
            );
            
            sendMessage(shopId, senderId, confirmationMessage);
            log.info("Created order {} for Facebook user {} in shop {}", createdOrder.getId(), senderId, shopId);
            
        } catch (Exception e) {
            log.error("Error processing order for Facebook user {}: {}", senderId, e.getMessage(), e);
            sendMessage(shopId, senderId, "I'm sorry, there was an error processing your order. Please try again later.");
        }
    }

    /**
     * Process address update for Facebook user and complete pending orders
     */
    private void processAddressUpdate(Long shopId, String senderId, String address) {
        try {
            // Generate email pattern for Facebook user
            String customerEmail = "facebook_" + senderId + "@example.com";
            
            // Find customer by email pattern to update address
            Customer customer = customerRepository.findByEmailAndShopId(customerEmail, shopId)
                .orElse(null);
                
            if (customer == null) {
                sendMessage(shopId, senderId, "Sorry, I couldn't find your customer information.");
                return;
            }
            
            // Update customer address
            customer.setAddress(address);
            customerRepository.save(customer);
            
            sendMessage(shopId, senderId, "✅ Address updated successfully!");
            
            // Check if there's a pending order
            PendingOrderService.PendingOrderInfo pendingOrder = pendingOrderService.getPendingOrder(senderId);
            if (pendingOrder != null) {            // Create order request using builder pattern
            CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                    .customerId(pendingOrder.getCustomerId())
                    .productId(pendingOrder.getProductId())
                    .quantity(pendingOrder.getQuantity())
                    .note(pendingOrder.getNote())
                    .build();
                
                // Call service to create order
                OrderDTO createdOrder = orderService.createOrder(orderRequest);
                
                // Remove the pending order after successful creation
                pendingOrderService.removePendingOrder(senderId);
                
                // Send order confirmation
                String confirmationMessage = String.format(
                    "✅ Your order has been created successfully!\n\n" +
                    "🔢 Order ID: #%d\n" +
                    "🛍️ Product: %s\n" +
                    "🔢 Quantity: %d\n" +
                    "🏷️ Status: %s\n" +
                    "🏠 Delivery address: %s\n\n" +
                    "Thank you for your purchase!",
                    createdOrder.getId(),
                    createdOrder.getProductName(),
                    createdOrder.getQuantity(),
                    createdOrder.getStatus().toString(),
                    address
                );
                
                sendMessage(shopId, senderId, confirmationMessage);
            }
        } catch (Exception e) {
            log.error("Error updating address for Facebook user {}: {}", senderId, e.getMessage(), e);
            sendMessage(shopId, senderId, "There was an error updating your address: " + e.getMessage());
        }
    }    // Helper method to find a shop ID from a Facebook page ID
    private Long findShopIdByPageId(String pageId) {
        try {
            // Look for active Facebook configuration for this specific page ID
            Optional<FacebookAccessToken> tokenEntity = facebookAccessTokenRepository
                    .findByPageIdAndActive(pageId, true);
            
            if (tokenEntity.isPresent()) {
                log.info("Found shop {} for Facebook page {}", tokenEntity.get().getShopId(), pageId);
                return tokenEntity.get().getShopId();
            }
            
            log.warn("No active Facebook configuration found for page ID: {}", pageId);
            return null;
        } catch (Exception e) {
            log.error("Error finding shop for page ID {}: {}", pageId, e.getMessage(), e);
            return null;
        }
    }    @Override
    public void sendMessage(Long shopId, String recipientId, String message) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findFirstByShopIdAndActive(shopId, true)
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
        
        boolean success = false;
        String errorMessage = null;
        
        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            success = true;
            log.debug("Successfully sent message to Facebook user {}", recipientId);
        } catch (Exception e) {
            errorMessage = e.getMessage();
            log.error("Error sending message to Facebook: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send message to Facebook", e);        } finally {
            // Log outgoing message if monitoring is available
            try {
                if (facebookMonitoringService != null) {
                    facebookMonitoringService.logOutgoingMessage(shopId, tokenEntity.getPageId(), 
                            "system", recipientId, message, success, errorMessage);
                }
            } catch (Exception monitoringException) {
                log.debug("Monitoring service unavailable: {}", monitoringException.getMessage());
            }
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

    @Override
    public void savePageConfiguration(Long shopId, FacebookWebhookConfigDto.CreateRequest request) {
        try {
            // Check if this page is already configured for any shop
            Optional<FacebookAccessToken> existingPage = facebookAccessTokenRepository.findByPageId(request.getPageId());
            if (existingPage.isPresent()) {
                throw new RuntimeException("This Facebook page is already configured for another shop");
            }
              // Validate access token with Facebook API
            if (!facebookPageValidationService.validatePageAccessToken(request.getAccessToken(), request.getPageId())) {
                throw new RuntimeException("Invalid page access token or page ID");
            }
            
            // Create new configuration
            FacebookAccessToken tokenEntity = FacebookAccessToken.builder()
                    .shopId(shopId)
                    .pageId(request.getPageId())
                    .pageName(request.getPageName())
                    .accessToken(request.getAccessToken())
                    .verifyToken(request.getVerifyToken())
                    .webhookUrl(request.getWebhookUrl())
                    .subscribedEvents(request.getSubscribedEvents() != null ? 
                            String.join(",", request.getSubscribedEvents()) : "messages,messaging_postbacks")
                    .active(true)
                    .build();
            
            facebookAccessTokenRepository.save(tokenEntity);
            
            // Subscribe page to webhook
            subscribePageToWebhook(shopId, request.getPageId());
            
            log.info("Successfully configured Facebook page {} for shop {}", request.getPageId(), shopId);
            
        } catch (Exception e) {
            log.error("Error saving page configuration for shop {}: {}", shopId, e.getMessage(), e);
            throw new RuntimeException("Failed to save page configuration: " + e.getMessage());
        }
    }
    
    @Override
    public void updatePageConfiguration(Long shopId, String pageId, FacebookWebhookConfigDto.UpdateRequest request) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByPageIdAndActive(pageId, true)
                    .orElseThrow(() -> new EntityNotFoundException("Facebook page configuration not found"));
            
            if (!tokenEntity.getShopId().equals(shopId)) {
                throw new RuntimeException("This page does not belong to the specified shop");
            }
            
            // Update fields if provided
            if (request.getPageName() != null) {
                tokenEntity.setPageName(request.getPageName());
            }            if (request.getAccessToken() != null) {
                if (!facebookPageValidationService.validatePageAccessToken(request.getAccessToken(), pageId)) {
                    throw new RuntimeException("Invalid page access token");
                }
                tokenEntity.setAccessToken(request.getAccessToken());
            }
            if (request.getVerifyToken() != null) {
                tokenEntity.setVerifyToken(request.getVerifyToken());
            }
            if (request.getWebhookUrl() != null) {
                tokenEntity.setWebhookUrl(request.getWebhookUrl());
            }
            if (request.getSubscribedEvents() != null) {
                tokenEntity.setSubscribedEvents(String.join(",", request.getSubscribedEvents()));
            }
            if (request.getActive() != null) {
                tokenEntity.setActive(request.getActive());
            }
            
            facebookAccessTokenRepository.save(tokenEntity);
            
            log.info("Successfully updated Facebook page configuration {} for shop {}", pageId, shopId);
            
        } catch (Exception e) {
            log.error("Error updating page configuration for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            throw new RuntimeException("Failed to update page configuration: " + e.getMessage());
        }
    }
    
    @Override
    public void deletePageConfiguration(Long shopId, String pageId) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByPageIdAndActive(pageId, true)
                    .orElseThrow(() -> new EntityNotFoundException("Facebook page configuration not found"));
            
            if (!tokenEntity.getShopId().equals(shopId)) {
                throw new RuntimeException("This page does not belong to the specified shop");
            }
            
            // Unsubscribe from webhook first
            try {
                unsubscribePageFromWebhook(shopId, pageId);
            } catch (Exception e) {
                log.warn("Failed to unsubscribe page from webhook: {}", e.getMessage());
            }
            
            // Delete configuration
            facebookAccessTokenRepository.delete(tokenEntity);
            
            log.info("Successfully deleted Facebook page configuration {} for shop {}", pageId, shopId);
            
        } catch (Exception e) {
            log.error("Error deleting page configuration for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete page configuration: " + e.getMessage());
        }
    }
      @Override
    public List<FacebookWebhookConfigDto> getShopPageConfigurations(Long shopId) {
        try {
            List<FacebookAccessToken> configurations = facebookAccessTokenRepository.findAllByShopId(shopId);
            
            return configurations.stream()
                    .map(config -> FacebookWebhookConfigDto.builder()
                            .pageId(config.getPageId())
                            .pageName(config.getPageName())
                            .webhookUrl(config.getWebhookUrl())
                            .verifyToken(config.getVerifyToken())                            .subscribedEvents(config.getSubscribedEvents() != null ? 
                                    Arrays.asList(config.getSubscribedEvents().split(",")) : Arrays.asList())
                            .active(config.isActive())
                            .build())
                    .collect(java.util.stream.Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error getting page configurations for shop {}: {}", shopId, e.getMessage(), e);
            throw new RuntimeException("Failed to get page configurations: " + e.getMessage());
        }
    }
    
    @Override
    public FacebookWebhookConfigDto getPageConfiguration(Long shopId, String pageId) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByPageId(pageId)
                    .orElseThrow(() -> new EntityNotFoundException("Facebook page configuration not found"));
            
            if (!tokenEntity.getShopId().equals(shopId)) {
                throw new RuntimeException("This page does not belong to the specified shop");
            }
            
            return FacebookWebhookConfigDto.builder()
                    .pageId(tokenEntity.getPageId())
                    .pageName(tokenEntity.getPageName())
                    .webhookUrl(tokenEntity.getWebhookUrl())
                    .verifyToken(tokenEntity.getVerifyToken())
                    .subscribedEvents(tokenEntity.getSubscribedEvents() != null ? 
                            List.of(tokenEntity.getSubscribedEvents().split(",")) : List.of())
                    .active(tokenEntity.isActive())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error getting page configuration for shop {} and page {}: {}", shopId, pageId, e.getMessage(), e);
            throw new RuntimeException("Failed to get page configuration: " + e.getMessage());
        }
    }
    
    @Override
    public void subscribePageToWebhook(Long shopId, String pageId) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByPageIdAndActive(pageId, true)
                    .orElseThrow(() -> new EntityNotFoundException("Active Facebook page configuration not found"));
            
            if (!tokenEntity.getShopId().equals(shopId)) {
                throw new RuntimeException("This page does not belong to the specified shop");
            }
            
            String url = facebookApiUrl + "/" + pageId + "/subscribed_apps?access_token=" + tokenEntity.getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Subscribe to specific events
            Map<String, Object> requestBody = new HashMap<>();
            if (tokenEntity.getSubscribedEvents() != null && !tokenEntity.getSubscribedEvents().isEmpty()) {
                requestBody.put("subscribed_fields", tokenEntity.getSubscribedEvents());
            } else {
                requestBody.put("subscribed_fields", "messages,messaging_postbacks");
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            log.info("Successfully subscribed page {} to webhook for shop {}", pageId, shopId);
            
        } catch (Exception e) {
            log.error("Error subscribing page {} to webhook for shop {}: {}", pageId, shopId, e.getMessage(), e);
            throw new RuntimeException("Failed to subscribe page to webhook: " + e.getMessage());
        }
    }
    
    @Override
    public void unsubscribePageFromWebhook(Long shopId, String pageId) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByPageId(pageId)
                    .orElseThrow(() -> new EntityNotFoundException("Facebook page configuration not found"));
            
            if (!tokenEntity.getShopId().equals(shopId)) {
                throw new RuntimeException("This page does not belong to the specified shop");
            }
            
            String url = facebookApiUrl + "/" + pageId + "/subscribed_apps?access_token=" + tokenEntity.getAccessToken();
            
            restTemplate.exchange(url, HttpMethod.DELETE, null, String.class);
            
            log.info("Successfully unsubscribed page {} from webhook for shop {}", pageId, shopId);
            
        } catch (Exception e) {
            log.error("Error unsubscribing page {} from webhook for shop {}: {}", pageId, shopId, e.getMessage(), e);
            throw new RuntimeException("Failed to unsubscribe page from webhook: " + e.getMessage());
        }    }
}