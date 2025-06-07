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
import com.g18.assistant.service.OrderService;
import com.g18.assistant.service.PendingOrderService;
import com.g18.assistant.service.ShopAIService;
import com.g18.assistant.service.ShopService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookBotServiceImpl implements FacebookBotService {    private final FacebookAccessTokenRepository facebookAccessTokenRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ShopAIService shopAIService;
    private final ShopService shopService;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final PendingOrderService pendingOrderService;

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
    }

    @Override
    public FacebookBotStatusDto getBotStatus(Long shopId) {
        FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopId(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Facebook configuration not found for shop: " + shopId));
        
        return FacebookBotStatusDto.builder()
                .shopId(shopId)
                .active(tokenEntity.isActive())
                .webhookUrl(tokenEntity.getWebhookUrl())                .build();
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
                            // Call our AI service to get a response
                            String aiResponse = shopAIService.processCustomerMessage(
                                    shopId, senderId, "Facebook User", messageText);
                            
                            // Parse the AI response
                            JsonNode responseJson = objectMapper.readTree(aiResponse);
                            
                            // Check if there was an error
                            if (responseJson.has("error") && responseJson.get("error").asBoolean()) {
                                log.error("AI error: {}", responseJson.get("message").asText());
                                sendMessage(shopId, senderId, "Xin lỗi, tôi đang gặp khó khăn trong việc hiểu yêu cầu của bạn. Vui lòng thử lại sau.");
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
                            sendMessage(shopId, senderId, "Xin lỗi, tôi không thể xử lý yêu cầu của bạn. Vui lòng thử lại sau.");
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
            // Get product details from the database using shopAIService
            Product product = shopAIService.getProductById(shopId, productId);
            if (product == null) {
                sendMessage(shopId, recipientId, "Xin lỗi, tôi không thể tìm thấy sản phẩm này.");
                return;
            }
            
            // Create detailed product description in Vietnamese
            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("🛍️ ").append(product.getName()).append("\n\n");
            detailsBuilder.append("💰 Giá: ").append(String.format("%,.0f", product.getPrice())).append(" VND\n");
            detailsBuilder.append("🏷️ Danh mục: ").append(product.getCategory()).append("\n");
            
            if (product.getStock() > 0) {
                detailsBuilder.append("✅ Còn hàng: ").append(product.getStock()).append(" sản phẩm\n");
            } else {
                detailsBuilder.append("❌ Hết hàng\n");
            }
            
            detailsBuilder.append("\n📝 Mô tả: ").append(product.getDescription());
            detailsBuilder.append("\n\nBạn có muốn đặt hàng sản phẩm này không?");
            
            String detailsText = detailsBuilder.toString();
            
            // If the product has an image, send with image
            if (product.getImageBase64() != null && !product.getImageBase64().isEmpty()) {
                sendProductImage(shopId, recipientId, product, detailsText);
            } else {
                // If no image, just send text details
                sendMessage(shopId, recipientId, detailsText);
            }
            
            log.info("Sent product details for product {} to Facebook user {}", productId, recipientId);
              } catch (Exception e) {
            log.error("Error sending product details to Facebook user {}: {}", recipientId, e.getMessage(), e);
            sendMessage(shopId, recipientId, "Xin lỗi, tôi không thể lấy thông tin chi tiết sản phẩm.");
        }
    }

    /**
     * Send product image with details to a Facebook user using Facebook's proper image upload
     */
    private void sendProductImage(Long shopId, String recipientId, Product product, String caption) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopIdAndActive(shopId, true)
                .orElseThrow(() -> new EntityNotFoundException("Active Facebook configuration not found for shop: " + shopId));
            
            String url = facebookApiUrl + "/me/messages?access_token=" + tokenEntity.getAccessToken();
            
            // First send image using proper Facebook API
            sendImageAttachment(shopId, recipientId, product.getImageBase64());
            
            // Then send the caption as a separate text message
            if (caption != null && !caption.trim().isEmpty()) {
                // Small delay to ensure proper message ordering
                Thread.sleep(500);
                sendMessage(shopId, recipientId, caption);
            }
            
        } catch (Exception e) {
            log.error("Error sending product image to Facebook user {}: {}", recipientId, e.getMessage(), e);
            // Fallback to text message if image sending fails
            sendMessage(shopId, recipientId, caption);
        }
    }    /**
     * Send image attachment to Facebook using proper file upload
     */
    private void sendImageAttachment(Long shopId, String recipientId, String imageBase64) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopIdAndActive(shopId, true)
                .orElseThrow(() -> new EntityNotFoundException("Active Facebook configuration not found for shop: " + shopId));
            
            // Step 1: Upload the image to Facebook first
            String attachmentId = uploadImageToFacebookAPI(tokenEntity.getAccessToken(), imageBase64);
            
            if (attachmentId != null) {
                // Step 2: Send message with uploaded attachment
                sendImageMessage(shopId, recipientId, attachmentId);
            } else {
                log.warn("Failed to upload image to Facebook, skipping image sending");
            }
            
        } catch (Exception e) {
            log.error("Error preparing image attachment for Facebook: {}", e.getMessage(), e);
        }
    }

    /**
     * Upload image to Facebook and get attachment ID
     */
    private String uploadImageToFacebookAPI(String accessToken, String imageBase64) {
        try {
            String uploadUrl = facebookApiUrl + "/me/message_attachments?access_token=" + accessToken;
            
            // Convert base64 to byte array
            byte[] imageBytes = java.util.Base64.getDecoder().decode(imageBase64);
            
            // Create multipart form data
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
              // Create the file part
            ByteArrayResource fileResource = new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "product-image.jpg";
                }
            };
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("message", "{\"attachment\":{\"type\":\"image\",\"payload\":{\"is_reusable\":true}}}");
            body.add("filedata", fileResource);
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                new HttpEntity<>(body, headers);
            
            // Upload the image
            String response = restTemplate.postForObject(uploadUrl, requestEntity, String.class);
            
            if (response != null) {
                // Parse response to get attachment_id
                JsonNode responseJson = objectMapper.readTree(response);
                if (responseJson.has("attachment_id")) {
                    String attachmentId = responseJson.get("attachment_id").asText();
                    log.info("Successfully uploaded image to Facebook with attachment ID: {}", attachmentId);
                    return attachmentId;
                }
            }
            
        } catch (Exception e) {
            log.error("Error uploading image to Facebook API: {}", e.getMessage(), e);
        }
        
        return null;
    }

    /**
     * Send message with uploaded image attachment
     */
    private void sendImageMessage(Long shopId, String recipientId, String attachmentId) {
        try {
            FacebookAccessToken tokenEntity = facebookAccessTokenRepository.findByShopIdAndActive(shopId, true)
                .orElseThrow(() -> new EntityNotFoundException("Active Facebook configuration not found for shop: " + shopId));
            
            String url = facebookApiUrl + "/me/messages?access_token=" + tokenEntity.getAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> recipientMap = new HashMap<>();
            recipientMap.put("id", recipientId);
            
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("attachment_id", attachmentId);
            
            Map<String, Object> attachmentMap = new HashMap<>();
            attachmentMap.put("type", "image");
            attachmentMap.put("payload", payloadMap);
            
            Map<String, Object> messageMap = new HashMap<>();
            messageMap.put("attachment", attachmentMap);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("recipient", recipientMap);
            requestBody.put("message", messageMap);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("Successfully sent image message to Facebook user {}", recipientId);
            
        } catch (Exception e) {
            log.error("Error sending image message to Facebook: {}", e.getMessage(), e);
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
                sendMessage(shopId, senderId, "Xin lỗi, tôi không thể tìm thấy sản phẩm có ID: " + productId);
                return;
            }
            
            // Check if customer has address
            if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
                // Store pending order with the correct parameter order
                pendingOrderService.storePendingOrder(senderId, customer.getId(), productId, quantity, PendingOrderService.OrderSource.AI_CHAT);
                
                String message = String.format(
                    "📦 Tôi rất vui được đặt hàng cho bạn!\n\n" +
                    "🛍️ Sản phẩm: %s\n" +
                    "🔢 Số lượng: %d\n" +
                    "💰 Tổng cộng: %s VND\n\n" +
                    "📍 Để hoàn tất đơn hàng, vui lòng cung cấp địa chỉ giao hàng bằng cách nhập:\n" +
                    "/address [Địa chỉ đầy đủ của bạn]",
                    product.getName(),
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
                
                OrderDTO existingOrder = recentOrders.get(0);                String confirmationMessage = String.format(
                    "✅ Đơn hàng của bạn đã được xử lý thành công!\n\n" +
                    "🔢 Mã đơn hàng: #%d\n" +
                    "🛍️ Sản phẩm: %s\n" +
                    "🔢 Số lượng: %d\n" +
                    "🏷️ Trạng thái: %s\n\n" +
                    "📦 Địa chỉ giao hàng: %s\n\n" +
                    "Cảm ơn bạn đã đặt hàng!",
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
                "✅ Đơn hàng đã được tạo thành công!\n\n" +
                "🔢 Mã đơn hàng: #%d\n" +
                "🛍️ Sản phẩm: %s\n" +
                "🔢 Số lượng: %d\n" +
                "💰 Tổng cộng: %s VND\n" +
                "📦 Địa chỉ giao hàng: %s\n\n" +
                "Cảm ơn bạn đã đặt hàng! Chúng tôi sẽ xử lý đơn hàng sớm nhất có thể.",
                createdOrder.getId(),
                product.getName(),
                createdOrder.getQuantity(),
                String.format("%,.0f", product.getPrice().multiply(BigDecimal.valueOf(quantity))),
                customer.getAddress()
            );
            
            sendMessage(shopId, senderId, confirmationMessage);
            log.info("Created order {} for Facebook user {} in shop {}", createdOrder.getId(), senderId, shopId);
              } catch (Exception e) {
            log.error("Error processing order for Facebook user {}: {}", senderId, e.getMessage(), e);
            sendMessage(shopId, senderId, "Xin lỗi, đã có lỗi khi xử lý đơn hàng của bạn. Vui lòng thử lại sau.");
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
                sendMessage(shopId, senderId, "Xin lỗi, tôi không thể tìm thấy thông tin khách hàng của bạn.");
                return;
            }
            
            // Update customer address
            customer.setAddress(address);
            customerRepository.save(customer);
            
            sendMessage(shopId, senderId, "✅ Địa chỉ đã được cập nhật thành công!");
            
            // Check if there's a pending order
            PendingOrderService.PendingOrderInfo pendingOrder = pendingOrderService.getPendingOrder(senderId);
            if (pendingOrder != null) {
                // Create order request using builder pattern
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
                    "✅ Đơn hàng của bạn đã được tạo thành công!\n\n" +
                    "🔢 Mã đơn hàng: #%d\n" +
                    "🛍️ Sản phẩm: %s\n" +
                    "🔢 Số lượng: %d\n" +
                    "🏷️ Trạng thái: %s\n" +
                    "🏠 Địa chỉ giao hàng: %s\n\n" +
                    "Cảm ơn bạn đã mua hàng!",
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
            sendMessage(shopId, senderId, "Đã có lỗi khi cập nhật địa chỉ của bạn: " + e.getMessage());
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