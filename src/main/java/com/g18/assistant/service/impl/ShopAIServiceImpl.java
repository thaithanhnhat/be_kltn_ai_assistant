package com.g18.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.dto.request.UpdateOrderStatusRequest;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Order.OrderStatus;
import com.g18.assistant.entity.Product;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.ConversationHistoryService;
import com.g18.assistant.service.CustomerService;
import com.g18.assistant.service.OrderService;
import com.g18.assistant.service.PendingOrderService;
import com.g18.assistant.service.ProductService;
import com.g18.assistant.service.ShopAIService;
import com.g18.assistant.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopAIServiceImpl implements ShopAIService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final ShopService shopService;    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ConversationHistoryService conversationHistoryService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final PendingOrderService pendingOrderService;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;
    
    @Value("${app.gemini.chat-model-id}")
    private String geminiChatModel;
      @Value("${app.gemini.api-url}")
    private String geminiApiUrl;
    
    @Override
    public String processCustomerMessage(Long shopId, String customerId, String customerName, String message) {
        try {
            // Track if an order has been created for this message to prevent duplicates
            boolean orderCreated = false;
            boolean orderCancelled = false;
            
            // Declare aiResponseJson outside the try-catch block so it can be accessed later
            JsonNode aiResponseJson = null;
            
            // Extract potentially useful customer information from the message
            Map<String, String> extractedInfo = customerService.extractCustomerInfoFromMessage(message);
              // Find or create a customer record
            Customer customer = null;
            
            // For Telegram users, use email pattern: telegram_{username}@example.com
            String customerEmail = null;
            if (customerId != null && !customerId.isEmpty()) {
                // Generate email pattern for Telegram user
                customerEmail = "telegram_" + customerId + "@example.com";
                
                // Try to find by email pattern first
                customer = customerService.findByEmailAndShopId(customerEmail, shopId);
                
                // If still not found, create a new customer with email pattern
                if (customer == null) {
                    customer = customerService.createNewCustomer(
                        shopId, 
                        customerId, 
                        customerName,
                        customerEmail
                    );
                }
                
                // Update customer with any new information from the message
                if (!extractedInfo.isEmpty()) {
                    customerService.updateCustomerInfo(customer.getId(), extractedInfo);
                    log.info("Updated customer information from message for customer ID: {}, Info: {}", customer.getId(), extractedInfo);
                }
            }
            
            // Check if the message is about asking what they've asked before or similar queries
            String lowerMessage = message.toLowerCase();
            if (lowerMessage.contains("tôi hỏi bạn những gì rồi") || 
                lowerMessage.contains("mình hỏi bạn những gì") || 
                lowerMessage.contains("tôi đã hỏi gì") || 
                lowerMessage.contains("mình đã hỏi gì") ||
                (lowerMessage.contains("vừa") && lowerMessage.contains("hỏi") && lowerMessage.contains("gì")) ||
                (lowerMessage.contains("tôi") && lowerMessage.contains("hỏi") && lowerMessage.contains("trước"))) {
                
                // Get the history first to check if there's anything in the current session
                List<ConversationHistoryService.ConversationEntry> currentHistory = 
                    conversationHistoryService.getRecentHistory(shopId, customerId, 10);
                
                if (currentHistory.size() <= 1) {
                    // If this is the first message or only message, clear any lingering history and inform user
                    conversationHistoryService.clearHistory(shopId, customerId);
                    conversationHistoryService.addMessage(shopId, customerId, "customer", message);
                    
                    String response = "Chào bạn! Đây là cuộc trò chuyện đầu tiên của chúng ta trong phiên này. " +
                        "Bạn chưa hỏi tôi câu hỏi nào trước đó. Tôi có thể giúp gì cho bạn?";
                    
                    conversationHistoryService.addMessage(shopId, customerId, "assistant", response);
                    
                    ObjectNode responseJson = objectMapper.createObjectNode();
                    responseJson.put("response_text", response);
                    responseJson.put("detected_intent", "GREETING");
                    responseJson.put("needs_shop_context", false);
                    responseJson.put("error", false);
                    return responseJson.toString();
                }
            }
            
            // Lưu message của khách vào lịch sử
            conversationHistoryService.addMessage(shopId, customerId, "customer", message);

            // Lấy lịch sử hội thoại gần nhất
            List<ConversationHistoryService.ConversationEntry> history = conversationHistoryService.getRecentHistory(shopId, customerId, 10);

            // Format history string
            String historyStr = formatConversationHistory(history);
              // Check if there's a pending order for this customer
            String customerKey = shopId + ":" + customerId;
            PendingOrderService.PendingOrderInfo pendingOrder = pendingOrderService.getPendingOrder(customerKey);
            
            // Check if this message is likely just providing an address (quick check before AI call)
            boolean isLikelyAddressOnly = isProbablyAddressOnly(message, history);

            // First, analyze the intent without loading all shop data
            String intentAnalysis = analyzeMessageIntent(message, history);
            JsonNode analysisJson = objectMapper.readTree(intentAnalysis);
            if (analysisJson.has("error") && analysisJson.get("error").asBoolean()) {
                return intentAnalysis;
            }
            
            // Extract address and phone from AI's analysis if available
            String extractedAddress = null;
            String extractedPhone = null;
            
            if (analysisJson.has("extracted_address") && !analysisJson.get("extracted_address").isNull() && !analysisJson.get("extracted_address").asText().isEmpty()) {
                extractedAddress = analysisJson.get("extracted_address").asText();
                log.info("AI extracted address: {}", extractedAddress);
            }
            
            if (analysisJson.has("extracted_phone") && !analysisJson.get("extracted_phone").isNull() && !analysisJson.get("extracted_phone").asText().isEmpty()) {
                extractedPhone = analysisJson.get("extracted_phone").asText();
                log.info("AI extracted phone: {}", extractedPhone);
            }
            
            // Update customer info with address and phone if available
            if (customer != null && (extractedAddress != null || extractedPhone != null)) {
                Map<String, String> customerUpdates = new HashMap<>();
                
                if (extractedAddress != null && (customer.getAddress() == null || customer.getAddress().isEmpty() 
                    || customer.getAddress().equals("Đang cập nhật"))) {
                    customerUpdates.put("address", extractedAddress);
                }
                
                if (extractedPhone != null && (customer.getPhone() == null || customer.getPhone().isEmpty())) {
                    customerUpdates.put("phone", extractedPhone);
                }
                
                if (!customerUpdates.isEmpty()) {
                    customer = customerService.updateCustomerInfo(customer.getId(), customerUpdates);
                    log.info("Updated customer with AI extracted information: {}", customerUpdates);
                }
            }
            
            String detectedIntent = analysisJson.path("detected_intent").asText("GENERAL_QUERY");
            
            // Handle ADDRESS_RESPONSE with create_order field
            if ("ADDRESS_RESPONSE".equals(detectedIntent) && customer != null) {
                log.info("Processing address response with potential order creation");
                
                boolean createOrder = analysisJson.path("create_order").asBoolean(false);
                boolean actionRequired = analysisJson.path("action_required").asBoolean(false);
                
                // Check if we should create an order from explicit action_details
                if (actionRequired && analysisJson.has("action_details") && 
                    analysisJson.path("action_details").has("action_type") &&
                    "PLACEORDER".equals(analysisJson.path("action_details").path("action_type").asText()) &&
                    analysisJson.path("action_details").has("product_id") && 
                    analysisJson.path("action_details").has("quantity")) {
                    
                    JsonNode actionDetails = analysisJson.get("action_details");
                    Long productId = actionDetails.get("product_id").asLong();
                    int quantity = actionDetails.get("quantity").asInt();
                    
                    // Check if we have a valid address now
                    boolean hasValidAddress = extractedAddress != null || 
                                             (customer.getAddress() != null && 
                                              !customer.getAddress().isEmpty() && 
                                              !customer.getAddress().equals("Đang cập nhật"));
                    
                    if (hasValidAddress && !orderCreated) {
                        // Create the order directly from the address response
                        saveOrderFromAI(customer.getId(), productId, quantity);
                        orderCreated = true;                        log.info("Created order directly from address response with PLACEORDER action: Product ID: {}, Quantity: {}", 
                                productId, quantity);
                                
                        // Remove any pending orders
                        pendingOrderService.removePendingOrder(customerKey);
                        
                        // We can return the response immediately since we've processed the order
                        return intentAnalysis;
                    }
                }
                // Fallback to the create_order flag (may be used in future responses)
                else if (createOrder && actionRequired && analysisJson.has("action_details") && 
                    analysisJson.path("action_details").has("product_id") && 
                    analysisJson.path("action_details").has("quantity")) {
                    
                    JsonNode actionDetails = analysisJson.get("action_details");
                    Long productId = actionDetails.get("product_id").asLong();
                    int quantity = actionDetails.get("quantity").asInt();
                    
                    // Check if we have a valid address now
                    boolean hasValidAddress = extractedAddress != null || 
                                             (customer.getAddress() != null && 
                                              !customer.getAddress().isEmpty() && 
                                              !customer.getAddress().equals("Đang cập nhật"));
                    
                    if (hasValidAddress && !orderCreated) {
                        // Create the order directly from the address response
                        saveOrderFromAI(customer.getId(), productId, quantity);
                        orderCreated = true;                        log.info("Created order directly from address response with create_order field: Product ID: {}, Quantity: {}", 
                                productId, quantity);
                                
                        // Remove any pending orders
                        pendingOrderService.removePendingOrder(customerKey);
                          // We can return the response immediately since we've processed the order                        return intentAnalysis;
                    }
                }
                  // If this is just an address response but no explicit order creation, check for pending orders
                if (!orderCreated) {
                    if (pendingOrder != null) {
                        // If no explicit create_order field but we have a pending order, use that
                        Long productId = pendingOrder.getProductId();
                        int quantity = pendingOrder.getQuantity();
                        
                        // Check if we have a valid address now
                        boolean hasValidAddress = extractedAddress != null || 
                                                 (customer.getAddress() != null && 
                                                  !customer.getAddress().isEmpty() && 
                                                  !customer.getAddress().equals("Đang cập nhật"));
                        
                        if (hasValidAddress) {
                            // Create the order using the pending information
                            saveOrderFromAI(customer.getId(), productId, quantity);
                            orderCreated = true;                            log.info("Created order from pending request after address response: Product ID: {}, Quantity: {}", 
                                    productId, quantity);
                            
                            // Remove the pending order
                            pendingOrderService.removePendingOrder(customerKey);
                        }
                    }
                }
                
                // If this is just an address response, return immediately
                boolean needsShopContext = analysisJson.path("needs_shop_context").asBoolean(false);
                if (!needsShopContext) {
                    log.info("Returning address response without further processing");
                    return intentAnalysis;
                }
            }
            
            // Only check for missing address when the customer is trying to place an order
            if ("PLACEORDER".equals(detectedIntent) && customer != null) {
                String addressCheckResponse = checkMissingAddressForOrder(customer);
                if (addressCheckResponse != null) {
                    return addressCheckResponse;
                }
            }
            
            // Check if we should store information about a pending order
            if ("PLACEORDER".equals(detectedIntent) && 
                analysisJson.has("action_required") && 
                analysisJson.get("action_required").asBoolean() &&
                analysisJson.has("action_details") && 
                analysisJson.path("action_details").has("product_id") && 
                analysisJson.path("action_details").has("quantity")) {
                
                JsonNode actionDetails = analysisJson.get("action_details");
                Long productId = actionDetails.get("product_id").asLong();
                int quantity = actionDetails.get("quantity").asInt();
                
                // Check if address is missing
                boolean needsAddress = customer == null || 
                    customer.getAddress() == null || 
                    customer.getAddress().isEmpty() || 
                    customer.getAddress().equals("Đang cập nhật");
                  if (needsAddress && !orderCreated) {
                    // Store pending order details
                    pendingOrderService.storePendingOrder(customerKey, customer.getId(), productId, quantity, 
                            PendingOrderService.OrderSource.AI_CHAT);
                    log.info("Stored pending order for customer {}: Product ID: {}, Quantity: {}", 
                           customerKey, productId, quantity);
                }
            }
            
            // Process order from initial intent analysis only if it has all required information
            // and we're handling a simple query that doesn't need full shop context
            if (!orderCreated && "PLACEORDER".equals(detectedIntent) && 
                analysisJson.has("action_required") && analysisJson.get("action_required").asBoolean() &&
                analysisJson.has("action_details") && customer != null) {
                
                // First, check if address is listed as missing information
                boolean addressIsMissing = false;
                if (analysisJson.has("missing_information") && analysisJson.get("missing_information").isArray()) {
                    JsonNode missingInfoArray = analysisJson.get("missing_information");
                    for (JsonNode item : missingInfoArray) {
                        String missingItem = item.asText();
                        if (missingItem.contains("address") || missingItem.contains("địa chỉ") || 
                            missingItem.equals("delivery_address") || missingItem.equals("shipping_address")) {
                            addressIsMissing = true;
                            log.info("Not creating order yet because address is listed as missing information");
                            break;
                        }
                    }
                }
                
                // Only proceed if address is not listed as missing
                if (!addressIsMissing) {
                    JsonNode actionDetails = analysisJson.get("action_details");
                    if (actionDetails.has("action_type") && 
                        "PLACEORDER".equals(actionDetails.get("action_type").asText()) && 
                        actionDetails.has("product_id") && actionDetails.has("quantity")) {
                        
                        Long productId = actionDetails.get("product_id").asLong();
                        int quantity = actionDetails.get("quantity").asInt();
                        
                        // Only attempt to place order if we have the required address
                        if (extractedAddress != null || 
                            (customer.getAddress() != null && !customer.getAddress().isEmpty() && 
                             !customer.getAddress().equals("Đang cập nhật"))) {
                            
                            // Save the order
                            saveOrderFromAI(customer.getId(), productId, quantity);
                            orderCreated = true;                            log.info("Order created from intent analysis: Product ID: {}, Quantity: {}", 
                                    productId, quantity);
                            
                            // Clear any pending orders
                            pendingOrderService.removePendingOrder(customerKey);
                        } else {
                            log.info("Not creating order yet because no address is available");
                            
                            // Store this as a pending order
                            pendingOrderService.storePendingOrder(customerKey, customer.getId(), productId, quantity, 
                                    PendingOrderService.OrderSource.AI_CHAT);
                            log.info("Stored pending order for customer {}: Product ID: {}, Quantity: {}", 
                                   customerKey, productId, quantity);
                        }
                    }
                }
            }
            
            boolean needsShopContext = analysisJson.path("needs_shop_context").asBoolean(false);
            if (!needsShopContext || "GENERAL_QUERY".equals(detectedIntent)) {
                log.info("Simple query detected, responding without shop context: {}", detectedIntent);
                return intentAnalysis;
            }
            log.info("Complex query detected, fetching shop context: {}", detectedIntent);
            Shop shop = shopService.getShopByIdForBotServices(shopId);
            Pageable pageable = PageRequest.of(0, 5);
            List<ProductResponse> products = new ArrayList<>(productService.getShopProducts(shopId, pageable).getContent());
            
            // Lấy danh sách thể loại sản phẩm
            List<String> categories = productService.getShopCategories(shopId);
            // Tạo prompt với lịch sử hội thoại và danh sách thể loại
            String prompt = historyStr + buildAIPrompt(shop, products, customer, customerName, message, categories);
            String aiResponse = callGeminiWithStructuredFormat(prompt);
            
            // Process order if the AI identified the intent as PLACEORDER
            try {
                aiResponseJson = objectMapper.readTree(aiResponse);
                
                // Check if there are additional address/phone values in the full AI response
                if (aiResponseJson.has("extracted_address") && !aiResponseJson.get("extracted_address").isNull() 
                    && !aiResponseJson.get("extracted_address").asText().isEmpty()) {
                    extractedAddress = aiResponseJson.get("extracted_address").asText();
                    log.info("Full AI response extracted address: {}", extractedAddress);
                }
                
                if (aiResponseJson.has("extracted_phone") && !aiResponseJson.get("extracted_phone").isNull() 
                    && !aiResponseJson.get("extracted_phone").asText().isEmpty()) {
                    extractedPhone = aiResponseJson.get("extracted_phone").asText();
                    log.info("Full AI response extracted phone: {}", extractedPhone);
                }
                
                // Update customer again if new information was found
                if (customer != null && (extractedAddress != null || extractedPhone != null)) {
                    Map<String, String> customerUpdates = new HashMap<>();
                    
                    if (extractedAddress != null && (customer.getAddress() == null || customer.getAddress().isEmpty() 
                        || customer.getAddress().equals("Đang cập nhật"))) {
                        customerUpdates.put("address", extractedAddress);
                    }
                    
                    if (extractedPhone != null && (customer.getPhone() == null || customer.getPhone().isEmpty())) {
                        customerUpdates.put("phone", extractedPhone);
                    }
                    
                    if (!customerUpdates.isEmpty()) {
                        customer = customerService.updateCustomerInfo(customer.getId(), customerUpdates);
                        log.info("Updated customer with full AI extracted information: {}", customerUpdates);
                    }
                }
                
                // Get the full response intent
                String fullResponseIntent = aiResponseJson.path("detected_intent").asText("GENERAL_QUERY");
                
                // Handle ADDRESS_RESPONSE with create_order in full AI response
                if ("ADDRESS_RESPONSE".equals(fullResponseIntent) && customer != null && !orderCreated) {
                    boolean createOrder = aiResponseJson.path("create_order").asBoolean(false);
                    boolean actionRequired = aiResponseJson.path("action_required").asBoolean(false);
                    
                    // Check if we should create an order from explicit action_details
                    if (actionRequired && aiResponseJson.has("action_details") && 
                        aiResponseJson.path("action_details").has("action_type") &&
                        "PLACEORDER".equals(aiResponseJson.path("action_details").path("action_type").asText()) &&
                        aiResponseJson.path("action_details").has("product_id") && 
                        aiResponseJson.path("action_details").has("quantity")) {
                        
                        JsonNode actionDetails = aiResponseJson.get("action_details");
                        Long productId = actionDetails.get("product_id").asLong();
                        int quantity = actionDetails.get("quantity").asInt();
                        
                        // Check if we have a valid address now
                        boolean hasValidAddress = extractedAddress != null || 
                                                (customer.getAddress() != null && 
                                                !customer.getAddress().isEmpty() && 
                                                !customer.getAddress().equals("Đang cập nhật"));
                        
                        if (hasValidAddress) {
                            // Create the order directly from the address response
                            saveOrderFromAI(customer.getId(), productId, quantity);
                            orderCreated = true;                            log.info("Created order from full AI address response with PLACEORDER action: Product ID: {}, Quantity: {}", 
                                    productId, quantity);
                                    
                            // Remove any pending orders
                            pendingOrderService.removePendingOrder(customerKey);
                        }
                    }
                    // Fallback to the create_order flag (may be used in future responses)
                    else if (createOrder && actionRequired && aiResponseJson.has("action_details") && 
                        aiResponseJson.path("action_details").has("product_id") && 
                        aiResponseJson.path("action_details").has("quantity")) {
                        
                        JsonNode actionDetails = aiResponseJson.get("action_details");
                        Long productId = actionDetails.get("product_id").asLong();
                        int quantity = actionDetails.get("quantity").asInt();
                        
                        // Check if we have a valid address now
                        boolean hasValidAddress = extractedAddress != null || 
                                                (customer.getAddress() != null && 
                                                !customer.getAddress().isEmpty() && 
                                                !customer.getAddress().equals("Đang cập nhật"));
                        
                        if (hasValidAddress) {
                            // Create the order directly from the address response
                            saveOrderFromAI(customer.getId(), productId, quantity);
                            orderCreated = true;                            log.info("Created order from full AI address response with create_order: Product ID: {}, Quantity: {}", 
                                    productId, quantity);
                                    
                            // Remove any pending orders
                            pendingOrderService.removePendingOrder(customerKey);
                        }                    } else if (pendingOrder != null) {
                        // If no explicit create_order field but we have a pending order, use that
                        Long productId = pendingOrder.getProductId();
                        int quantity = pendingOrder.getQuantity();
                        
                        // Check if we have a valid address now
                        boolean hasValidAddress = extractedAddress != null || 
                                                (customer.getAddress() != null && 
                                                !customer.getAddress().isEmpty() && 
                                                !customer.getAddress().equals("Đang cập nhật"));
                        
                        if (hasValidAddress) {
                            // Create the order using the pending information
                            saveOrderFromAI(customer.getId(), productId, quantity);
                            orderCreated = true;                            log.info("Created order from pending request after full AI address response: Product ID: {}, Quantity: {}", 
                                    productId, quantity);
                            
                            // Remove the pending order
                            pendingOrderService.removePendingOrder(customerKey);
                        }
                    }
                }
                
                // Only process order if one hasn't been created yet and this isn't just an address response
                if (!orderCreated && 
                    "PLACEORDER".equals(fullResponseIntent) && 
                    aiResponseJson.has("action_required") && 
                    aiResponseJson.get("action_required").asBoolean() &&
                    aiResponseJson.has("action_details")) {
                    
                    // Check if address is listed as missing information
                    boolean addressIsMissing = false;
                    if (aiResponseJson.has("missing_information") && aiResponseJson.get("missing_information").isArray()) {
                        JsonNode missingInfoArray = aiResponseJson.get("missing_information");
                        for (JsonNode item : missingInfoArray) {
                            String missingItem = item.asText();
                            if (missingItem.contains("address") || missingItem.contains("địa chỉ") || 
                                missingItem.equals("delivery_address") || missingItem.equals("shipping_address")) {
                                addressIsMissing = true;
                                log.info("Not creating order yet because address is listed as missing information in full response");
                                break;
                            }
                        }
                    }
                    
                    // Only proceed if address isn't missing
                    if (!addressIsMissing) {
                        JsonNode actionDetails = aiResponseJson.get("action_details");
                        if (actionDetails.has("action_type") && 
                            "PLACEORDER".equals(actionDetails.get("action_type").asText()) && 
                            actionDetails.has("product_id") && 
                            actionDetails.has("quantity") && 
                            customer != null) {
                                
                            Long productId = actionDetails.get("product_id").asLong();
                            int quantity = actionDetails.get("quantity").asInt();
                            
                            // Extract address and phone from the customer's message or action details
                            String address = extractedAddress;
                            if (address == null) {
                                address = extractAddressFromMessage(message);
                            }
                            
                            String phone = extractedPhone;
                            if (phone == null) {
                                phone = extractPhoneFromMessage(message);
                            }
                            
                            // Update the customer record with address and phone if available
                            Map<String, String> customerUpdates = new HashMap<>();
                            if (address != null && !address.isEmpty() && 
                                (customer.getAddress() == null || customer.getAddress().isEmpty() || customer.getAddress().equals("Đang cập nhật"))) {
                                customerUpdates.put("address", address);
                                log.info("Updating customer address: {}", address);
                            }
                            
                            if (phone != null && !phone.isEmpty() && 
                                (customer.getPhone() == null || customer.getPhone().isEmpty())) {
                                customerUpdates.put("phone", phone);
                                log.info("Updating customer phone: {}", phone);
                            }
                            
                            // If we have customer info to update, do it now
                            if (!customerUpdates.isEmpty()) {
                                customer = customerService.updateCustomerInfo(customer.getId(), customerUpdates);
                                log.info("Updated customer information before order creation: {}", customerUpdates);
                            }
                            
                            // Do we have an address to proceed with order?
                            if (address != null || 
                               (customer.getAddress() != null && !customer.getAddress().isEmpty() && 
                                !customer.getAddress().equals("Đang cập nhật"))) {
                                // Save the order only once
                                saveOrderFromAI(customer.getId(), productId, quantity);
                                orderCreated = true;
                                  log.info("Order saved successfully from AI response: Product ID: {}, Quantity: {}, Customer ID: {}", 
                                        productId, quantity, customer.getId());
                                
                                // Clear any pending orders
                                pendingOrderService.removePendingOrder(customerKey);
                            } else {
                                log.info("Not creating order because no valid address is available");
                                
                                // Store as pending order
                                pendingOrderService.storePendingOrder(customerKey, customer.getId(), productId, quantity, 
                                        PendingOrderService.OrderSource.AI_CHAT);
                                log.info("Stored pending order from full AI for customer {}: Product ID: {}, Quantity: {}", 
                                       customerKey, productId, quantity);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("Error processing AI response for order creation: {}", ex.getMessage(), ex);
                // We don't want to fail the whole response if order creation fails
            }
            
            // Lưu phản hồi AI vào lịch sử
            try {
                JsonNode aiJson = objectMapper.readTree(aiResponse);
                String respText = aiJson.has("response_text") ? aiJson.get("response_text").asText() : aiResponse;
                conversationHistoryService.addMessage(shopId, customerId, "assistant", respText);
            } catch (Exception ex) {
                conversationHistoryService.addMessage(shopId, customerId, "assistant", aiResponse);
            }

            // Check for cancel order intent in the intent analysis
            if (!orderCancelled && "CANCELORDER".equals(detectedIntent) && 
                analysisJson.has("action_required") && analysisJson.get("action_required").asBoolean() &&
                analysisJson.has("action_details") && customer != null) {
                
                JsonNode actionDetails = analysisJson.get("action_details");
                if (actionDetails.has("action_type") && 
                    "CANCELORDER".equals(actionDetails.get("action_type").asText())) {
                    
                    // Check if order_id is provided
                    if (actionDetails.has("order_id")) {
                        try {
                            // Try to parse order_id as a Long
                            Long orderId = Long.parseLong(actionDetails.get("order_id").asText());
                            // Cancel the order
                            cancelOrderFromAI(orderId);
                            orderCancelled = true;
                            log.info("Order cancelled from intent analysis: Order ID: {}", orderId);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid order ID format: {}", actionDetails.get("order_id").asText());
                        }
                    } else {
                        // No specific order_id, try to find the most recent order for this customer
                        Long orderId = findRecentOrderId(customer.getId());
                        if (orderId != null) {
                            cancelOrderFromAI(orderId);
                            orderCancelled = true;
                            log.info("Most recent order cancelled from intent analysis: Order ID: {}", orderId);
                        } else {
                            log.info("No recent order found to cancel for customer ID: {}", customer.getId());
                        }
                    }
                }
            }

            // Only process order if one hasn't been created yet and this isn't just an address response
            String fullResponseIntent = aiResponseJson.path("detected_intent").asText("GENERAL_QUERY");
            
            // Handle order cancellation if that's the intent
            if (!orderCancelled && "CANCELORDER".equals(fullResponseIntent) && 
                aiResponseJson.has("action_required") && 
                aiResponseJson.get("action_required").asBoolean() &&
                aiResponseJson.has("action_details") && customer != null) {
                
                JsonNode actionDetails = aiResponseJson.get("action_details");
                if (actionDetails.has("action_type") && 
                    "CANCELORDER".equals(actionDetails.get("action_type").asText())) {
                    
                    // Check if order_id is provided
                    if (actionDetails.has("order_id")) {
                        try {
                            // Try to parse order_id as a Long
                            Long orderId = Long.parseLong(actionDetails.get("order_id").asText());
                            // Cancel the order
                            cancelOrderFromAI(orderId);
                            orderCancelled = true;
                            log.info("Order cancelled from full AI response: Order ID: {}", orderId);
                        } catch (NumberFormatException e) {
                            log.warn("Invalid order ID format: {}", actionDetails.get("order_id").asText());
                        }
                    } else {
                        // No specific order_id, try to find the most recent order for this customer
                        Long orderId = findRecentOrderId(customer.getId());
                        if (orderId != null) {
                            cancelOrderFromAI(orderId);
                            orderCancelled = true;
                            log.info("Most recent order cancelled from full AI response: Order ID: {}", orderId);
                        } else {
                            log.info("No recent order found to cancel for customer ID: {}", customer.getId());
                        }
                    }
                }
            }
            
            return aiResponse;
        } catch (Exception e) {
            log.error("Error processing customer message with AI: {}", e.getMessage(), e);
            return createErrorResponse("Error processing your message: " + e.getMessage());
        }
    }

    @Override
    public String getProductRecommendations(Long shopId, String customerQuery) {
        try {
            // Get shop information without user validation (for bots)
            Shop shop = shopService.getShopByIdForBotServices(shopId);
            
            // Get products from the shop (limit to 10 for recommendations)
            Pageable pageable = PageRequest.of(0, 10);
            List<ProductResponse> products = new ArrayList<>(productService.getShopProducts(shopId, pageable).getContent());
            
            // If we have a search query, try to find more specific products
            if (customerQuery != null && !customerQuery.isEmpty()) {
                List<ProductResponse> searchResults = new ArrayList<>(
                        productService.searchShopProducts(shopId, customerQuery, pageable).getContent());
                
                // If we found specific products, prioritize them
                if (!searchResults.isEmpty()) {
                    products = searchResults;
                }
            }
            
            // Create AI prompt for product recommendations
            String prompt = buildProductRecommendationPrompt(shop, products, customerQuery);
            
            // Call Gemini API with structured product recommendation format
            return callGeminiForProductRecommendations(prompt);
            
        } catch (Exception e) {
            log.error("Error getting product recommendations: {}", e.getMessage(), e);
            return createErrorResponse("Error finding products: " + e.getMessage());
        }
    }

    @Override
    public String processOrderRequest(Long shopId, String customerId, String orderRequest) {        try {
            // Get shop information without user validation (for bots)
            Shop shop = shopService.getShopByIdForBotServices(shopId);
            
            // Verify customer exists
            Customer customer = null;
            if (customerId != null && !customerId.isEmpty()) {
                // Generate email pattern for Telegram user
                String customerEmail = "telegram_" + customerId + "@example.com";
                
                // Try to find by email pattern
                Optional<Customer> customerOpt = customerRepository.findByEmailAndShopId(customerEmail, shopId);
                if (customerOpt.isPresent()) {
                    customer = customerOpt.get();
                } else {
                    return createErrorResponse("Customer information is required for placing an order");
                }
            } else {
                return createErrorResponse("Customer ID is required for placing an order");
            }
            
            // Create AI prompt for order processing
            String prompt = buildOrderProcessingPrompt(shop, customer, orderRequest);
            
            // Call Gemini API with structured order processing format
            return callGeminiForOrderProcessing(prompt);
            
        } catch (Exception e) {
            log.error("Error processing order request: {}", e.getMessage(), e);
            return createErrorResponse("Error processing your order: " + e.getMessage());
        }
    }

    @Override
    public String validateDeliveryInfo(String customerInput) {
        try {
            // Create AI prompt for delivery info validation
            String prompt = buildDeliveryInfoValidationPrompt(customerInput);
            
            // Call Gemini API with structured validation format
            return callGeminiForDeliveryValidation(prompt);
            
        } catch (Exception e) {
            log.error("Error validating delivery information: {}", e.getMessage(), e);
            return createErrorResponse("Error validating your information: " + e.getMessage());
        }
    }
    
    /**
     * Build a comprehensive prompt for the AI with context about the shop, products, and customer
     */
    private String buildAIPrompt(Shop shop, List<ProductResponse> products, Customer customer, String customerName, String message, List<String> categories) {
        StringBuilder prompt = new StringBuilder();
        
        // Shop information context
        prompt.append("SHOP INFORMATION:\n");
        prompt.append("Name: ").append(shop.getName()).append("\n");
        prompt.append("Status: ").append(shop.getStatus()).append("\n");
        
        // Product categories
        if (categories != null && !categories.isEmpty()) {
            prompt.append("\nPRODUCT CATEGORIES:\n");
            for (String category : categories) {
                prompt.append("- ").append(category).append("\n");
            }
            prompt.append("\n");
        }
        
        // Add products for context
        if (products != null && !products.isEmpty()) {
            prompt.append("AVAILABLE PRODUCTS (Sample for context):\n");
            for (int i = 0; i < Math.min(5, products.size()); i++) {
                ProductResponse product = products.get(i);
                prompt.append("- ").append(product.getName())
                      .append(" (ID: ").append(product.getId()).append(")")
                      .append("\n  Price: ").append(product.getPrice())
                      .append("\n  Category: ").append(product.getCategory())
                      .append("\n  Description: ").append(product.getDescription())
                      .append("\n  Stock: ").append(product.getStock())
                      .append("\n");
            }
            prompt.append("\n");
        }
          // Customer context with personalized approach
        if (customer != null) {
            prompt.append("CUSTOMER INFORMATION:\n");
            prompt.append("Name: ").append(customer.getFullname());
            prompt.append("\nAddress: ").append(customer.getAddress());
            prompt.append("\nPhone: ").append(customer.getPhone());
            prompt.append("\nEmail: ").append(customer.getEmail());
            prompt.append("\n\n");
            
            // Add address validation context
            String customerAddress = customer.getAddress();
            if (customerAddress == null || customerAddress.isEmpty() || customerAddress.equals("Đang cập nhật")) {
                prompt.append("IMPORTANT: Customer has NO VALID ADDRESS. For any PLACEORDER intent:\n");
                prompt.append("- Set action_required: false\n");
                prompt.append("- Add 'delivery_address' to missing_information\n");
                prompt.append("- Ask for delivery address before processing order\n\n");
            } else {
                prompt.append("Customer has valid address for delivery.\n\n");
            }
            
            prompt.append("This is a returning customer. Use their name naturally and reference their previous interactions if relevant.\n\n");
        } else {
            prompt.append("NEW CUSTOMER:\n");
            prompt.append("Name: '").append(customerName).append("'\n");
            prompt.append("This is a new customer with NO ADDRESS INFORMATION. For any PLACEORDER intent:\n");
            prompt.append("- Set action_required: false\n");
            prompt.append("- Add 'delivery_address' to missing_information\n");
            prompt.append("- Ask for delivery address before processing order\n");
            prompt.append("Be extra welcoming and take initiative to understand their needs.\n\n");
        }
        
        // CRITICAL SESSION MANAGEMENT INSTRUCTION
        prompt.append("CRITICAL SESSION MANAGEMENT INSTRUCTION:\n");
        prompt.append("You can ONLY reference messages from the current conversation session. ");
        prompt.append("DO NOT reference or acknowledge anything from past sessions. ");
        prompt.append("If a customer asks what they've asked before, only mention questions from THIS session. ");
        prompt.append("If they haven't asked anything in this session, tell them this is your first conversation in this session.\n\n");
        
        // Proactive conversation guidelines - cải tiến
        prompt.append("CONVERSATION GUIDELINES (EXTREMELY IMPORTANT):\n");
        prompt.append("1. When a customer asks 'what do you sell?' or similar questions:\n");
        prompt.append("   - ALWAYS provide specific categories with enthusiasm\n");
        prompt.append("   - Example: 'Shop mình chuyên bán các sản phẩm [list exact categories] ạ! Bạn đang quan tâm đến loại nào?'\n\n");
        
        prompt.append("2. When a customer asks about previous conversation:\n");
        prompt.append("   - Reference the conversation history provided above\n");
        prompt.append("   - Summarize previous interactions accurately\n");
        prompt.append("   - Example: 'Dạ vừa rồi bạn đã hỏi về [topic] và mình đã giới thiệu [summary]'\n\n");
        
        prompt.append("3. For new customers:\n");
        prompt.append("   - Introduce yourself and the shop warmly\n");
        prompt.append("   - Ask what they're looking for\n");
        prompt.append("   - Suggest popular categories\n\n");
        
        prompt.append("4. For returning customers:\n");
        prompt.append("   - Greet them by name\n");
        prompt.append("   - Reference previous interactions\n");
        prompt.append("   - Suggest products based on their history\n\n");
        
        prompt.append("5. For all customers:\n");
        prompt.append("   - Ask specific follow-up questions\n");
        prompt.append("   - Suggest complementary products\n");
        prompt.append("   - Always be helpful and specific\n");
        prompt.append("   - Avoid vague or generic responses\n\n");
        
        // CUSTOMER INFORMATION DETECTION (IMPORTANT)
        prompt.append("CUSTOMER INFORMATION DETECTION (EXTREMELY IMPORTANT):\n");
        prompt.append("1. Look for and detect customer details in messages, including:\n");
        prompt.append("   - Địa chỉ (Addresses): Any text mentioning địa chỉ, nơi ở, chỗ ở, etc.\n");
        prompt.append("   - Họ tên (Full names): Vietnamese full names when customer introduces themselves\n");
        prompt.append("   - Số điện thoại (Phone numbers): Vietnamese phone numbers starting with 0, +84, 84\n");
        prompt.append("   - Email: Any email addresses shared in the conversation\n");
        prompt.append("2. When customer provides this information, acknowledge it naturally\n");
        prompt.append("3. If a customer is placing an order and hasn't provided their address yet, ask for it\n");
        prompt.append("4. If you detect customer information, continue the conversation normally\n");
        prompt.append("5. ALWAYS extract and return any detected address in the 'extracted_address' field\n");
        prompt.append("6. ALWAYS extract and return any detected phone number in the 'extracted_phone' field\n");
        prompt.append("7. Example address detection: 'Giao hàng đến số 72 Hương An, Hương Trà, Thừa Thiên Huế nhé' → extracted_address: '72 Hương An, Hương Trà, Thừa Thiên Huế'\n");
        prompt.append("8. Example phone detection: 'Số điện thoại của mình là 0912345678' → extracted_phone: '0912345678'\n\n");
          // Thay thế phần ORDER PROCESSING GUIDELINES cũ
        prompt.append("ORDER PROCESSING GUIDELINES (CRITICAL - FOLLOW EXACTLY):\n");
        prompt.append("When handling PLACEORDER intent (customer wants to buy something):\n");
        prompt.append("1. FIRST CHECK: Does customer have valid delivery address?\n");
        prompt.append("   - If customer address is missing, null, empty, or 'Đang cập nhật': set action_required: false\n");
        prompt.append("   - Add 'delivery_address' to missing_information array\n");
        prompt.append("   - Ask customer for delivery address in response_text\n");
        prompt.append("   - DO NOT create order until valid address is provided\n");
        prompt.append("2. ONLY if customer has valid address, then include these fields in action_details:\n");
        prompt.append("   - product_id: The exact numeric ID of the product\n");
        prompt.append("   - quantity: The exact numeric quantity (default to 1 if unspecified)\n");
        prompt.append("   - action_type: Must be 'PLACEORDER'\n");
        prompt.append("3. For products mentioned by name, match to a specific product_id from available products\n");
        prompt.append("4. If multiple products are available, choose the most relevant one\n");
        prompt.append("5. If product ID cannot be determined, put action_required: false\n");
        prompt.append("6. Always confirm the order details in response_text\n");
        prompt.append("7. RULE: NEVER set action_required: true for PLACEORDER if missing_information contains any address-related field\n\n");
        
        // Add new section for order cancellation
        prompt.append("ORDER CANCELLATION GUIDELINES (CRITICAL - FOLLOW EXACTLY):\n");
        prompt.append("When handling CANCELORDER intent (customer wants to cancel an order):\n");
        prompt.append("1. ALWAYS include these fields in action_details:\n");
        prompt.append("   - action_type: Must be 'CANCELORDER'\n");
        prompt.append("   - order_id: The specific order ID if mentioned by customer\n");
        prompt.append("2. If customer doesn't mention a specific order ID, omit the order_id field\n"); 
        prompt.append("   and the system will cancel their most recent order\n");
        prompt.append("3. Set action_required: true for all cancellation requests\n");
        prompt.append("4. Always acknowledge the cancellation request in response_text\n");
        prompt.append("5. Use empathetic language when confirming cancellations\n\n");
        
        // Add example for order cancellation
        prompt.append("Example - Order Cancellation (CRITICAL):\n");
        prompt.append("If customer says: 'Tôi muốn hủy đơn hàng số 123'\n");
        prompt.append("You MUST respond with:\n");
        prompt.append("{\n");
        prompt.append("  \"response_text\": \"Vâng, mình sẽ giúp bạn hủy đơn hàng số 123. Yêu cầu hủy đơn hàng đã được xử lý!\",\n");
        prompt.append("  \"detected_intent\": \"CANCELORDER\",\n");
        prompt.append("  \"action_required\": true,\n");
        prompt.append("  \"action_details\": {\n");
        prompt.append("    \"action_type\": \"CANCELORDER\",\n");
        prompt.append("    \"order_id\": \"123\"\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        
        // Add example for cancelling most recent order
        prompt.append("Example - Cancel Most Recent Order (CRITICAL):\n");
        prompt.append("If customer says: 'Tôi muốn hủy đơn hàng vừa đặt'\n");
        prompt.append("You MUST respond with:\n");
        prompt.append("{\n");
        prompt.append("  \"response_text\": \"Vâng, mình sẽ giúp bạn hủy đơn hàng mới nhất. Yêu cầu hủy đơn hàng đã được xử lý!\",\n");
        prompt.append("  \"detected_intent\": \"CANCELORDER\",\n");
        prompt.append("  \"action_required\": true,\n");
        prompt.append("  \"action_details\": {\n");
        prompt.append("    \"action_type\": \"CANCELORDER\"\n");
        prompt.append("  }\n");
        prompt.append("}\n\n");
        
        // Thêm ví dụ cho Cocacola
        prompt.append("Example - Specific Order (CRITICAL):\n");
        if (!products.isEmpty()) {
            ProductResponse exampleProduct = products.get(0);
            prompt.append(String.format("If customer says: 'Mua 2 %s và giao đến 72 Hương An, Hương Trà, TT Huế'\n", exampleProduct.getName()));
            prompt.append("You MUST respond with:\n");
            prompt.append("{\n");
            prompt.append(String.format("  \"response_text\": \"Vâng, mình xác nhận đơn hàng 2 %s. Shop sẽ giao hàng đến địa chỉ 72 Hương An, Hương Trà, TT Huế!\",\n", exampleProduct.getName()));
            prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
            prompt.append("  \"extracted_address\": \"72 Hương An, Hương Trà, TT Huế\",\n");
            prompt.append("  \"action_required\": true,\n");
            prompt.append("  \"action_details\": {\n");
            prompt.append("    \"action_type\": \"PLACEORDER\",\n");
            prompt.append(String.format("    \"product_id\": %d,\n", exampleProduct.getId()));
            prompt.append("    \"quantity\": 2\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
        } else {
            prompt.append("If customer says: 'Mua 2 chai nước và giao đến số 72 đường Hương An'\n");
            prompt.append("You MUST respond with:\n");
            prompt.append("{\n");
            prompt.append("  \"response_text\": \"Vâng, mình xác nhận đơn hàng 2 chai nước. Shop sẽ giao hàng đến địa chỉ số 72 đường Hương An!\",\n");
            prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
            prompt.append("  \"extracted_address\": \"số 72 đường Hương An\",\n");
            prompt.append("  \"action_required\": true,\n");
            prompt.append("  \"action_details\": {\n");
            prompt.append("    \"action_type\": \"PLACEORDER\",\n");
            prompt.append("    \"product_id\": [ID of the most relevant product],\n");
            prompt.append("    \"quantity\": 2\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
        }
        
        // Product reference table and actions
        prompt.append("PRODUCT REFERENCE TABLE - USE FOR ORDER PROCESSING:\n");
        for (ProductResponse product : products) {
            prompt.append(String.format("ID: %d - %s - Price: %s - Category: %s\n", 
                                       product.getId(), 
                                       product.getName(),
                                       product.getPrice(),
                                       product.getCategory()));
        }
        prompt.append("\n");
        
        // Standard action codes instruction
        prompt.append("ACTION CODES (When system action required):\n");
        prompt.append("- GETPRODUCT: Get detailed information about a specific product\n");
        prompt.append("- SEARCHPRODUCT: Search for products matching keywords\n");
        prompt.append("- ADDTOCART: Add a product to the customer's cart\n");
        prompt.append("- PLACEORDER: Complete the order process\n");
        prompt.append("- CHECKORDER: Check order status\n");
        prompt.append("- CANCELORDER: Cancel an existing order\n");
        prompt.append("- SENDIMAGE: Send product images directly to customer\n");
        prompt.append("- SHOWPRODUCT: Show product details with image\n");
        prompt.append("- CONVERSATION_REFERENCE: Customer is asking about previous conversation\n");
        prompt.append("- ADDRESS_RESPONSE: Customer is providing address in response to a request\n");
        prompt.append("- GENERAL_QUERY: General question not fitting other categories\n\n");
        
        // Add specific instruction about customer providing address
        prompt.append("ADDRESS RESPONSE HANDLING (CRITICAL):\n");
        prompt.append("When a customer is ONLY providing their address in response to your request:\n");
        prompt.append("1. Use intent 'ADDRESS_RESPONSE' instead of 'PLACEORDER'\n");
        prompt.append("2. Set action_required to true\n");
        prompt.append("3. Include the address in extracted_address field\n");
        prompt.append("4. Confirm receipt of the address in response_text\n");
        prompt.append("5. Add create_order: true if this address is for an order that should be created immediately\n");
        prompt.append("6. Include complete order details in action_details:\n");
        prompt.append("   - action_type: 'PLACEORDER'\n");
        prompt.append("   - product_id: The ID of the product to order\n");
        prompt.append("   - quantity: The quantity to order\n\n");
          // Example for address response
        prompt.append("Example - Address Response with Order (CRITICAL - FOLLOW EXACTLY):\n");
        prompt.append("Conversation context: Customer said 'mih muốn đặt 5 chai coca' then you asked for address\n");
        prompt.append("Customer provides: 'mih ở 77 nguyễn huệ, thành phố huế. sdt của mih là 0327538428'\n");
        prompt.append("You MUST respond with:\n");
        prompt.append("{\n");
        prompt.append("  \"response_text\": \"Dạ vâng, em đã nhận được địa chỉ giao hàng là 77 Nguyễn Huệ, thành phố Huế và số điện thoại 0327538428. Em đang tiến hành lên đơn 5 chai Coca-Cola giao đến địa chỉ này cho mình nhé.\",\n");
        prompt.append("  \"detected_intent\": \"ADDRESS_RESPONSE\",\n");
        prompt.append("  \"extracted_address\": \"77 nguyễn huệ, thành phố huế\",\n");
        prompt.append("  \"extracted_phone\": \"0327538428\",\n");
        prompt.append("  \"action_required\": true,\n");
        prompt.append("  \"create_order\": true,\n");
        prompt.append("  \"action_details\": {\n");
        prompt.append("    \"action_type\": \"PLACEORDER\",\n");
        prompt.append("    \"product_id\": 1,\n");
        prompt.append("    \"quantity\": 5\n");
        prompt.append("  },\n");
        prompt.append("  \"needs_shop_context\": false\n");
        prompt.append("}\n\n");
        
        // Example for regular address response (no order needed)
        prompt.append("Example - Simple Address Response (No Order):\n");
        prompt.append("If you asked for address for account update (not for an order) and customer says: 'Tôi ở 45 Lê Lợi, Hà Nội'\n");
        prompt.append("You MUST respond with:\n");
        prompt.append("{\n");
        prompt.append("  \"response_text\": \"Cảm ơn bạn, mình đã cập nhật địa chỉ: 45 Lê Lợi, Hà Nội vào hồ sơ của bạn!\",\n");
        prompt.append("  \"detected_intent\": \"ADDRESS_RESPONSE\",\n");
        prompt.append("  \"extracted_address\": \"45 Lê Lợi, Hà Nội\",\n");
        prompt.append("  \"action_required\": false,\n");
        prompt.append("  \"create_order\": false\n");
        prompt.append("}\n\n");
        
        // Image sending capability
        prompt.append("IMAGE FEATURE (For product visuals):\n");
        prompt.append("For product queries, use these actions to show product images:\n");
        prompt.append("- SENDIMAGE: For multiple product images\n");
        prompt.append("- SHOWPRODUCT: For detailed product information with image\n");
        prompt.append("- Always specify product_id and set send_product_images to true\n");
        prompt.append("- For SENDIMAGE, include product_ids_for_images as array of IDs\n\n");
        
        // Enhanced response style guidelines - cải tiến
        prompt.append("RESPONSE QUALITY GUIDELINES (CRITICAL):\n");
        prompt.append("- Never give vague responses - always be specific and helpful\n");
        prompt.append("- Always respond in Vietnamese unless customer uses English\n");
        prompt.append("- Always be natural, never sound robotic or AI-like\n");
        prompt.append("- Use casual language with Vietnamese expressions\n");
        prompt.append("- Be proactive in asking questions to understand needs\n");
        prompt.append("- Show enthusiasm about products and shop\n");
        prompt.append("- Include shop name naturally in conversation\n");
        prompt.append("- For simple greetings, respond warmly and ask how you can help\n");
        prompt.append("- For product inquiries, ask about specific preferences\n");
        prompt.append("- For orders, be thorough while maintaining friendly tone\n\n");
        
        // Example conversations - mở rộng với ví dụ cho từng loại tình huống
        prompt.append("EXAMPLE DIALOGUES (Model your responses on these):\n");
        
        // Ví dụ 1: Chào hỏi
        prompt.append("Example 1 - Greeting:\n");
        prompt.append("Customer: 'Chào'\n");
        prompt.append("Assistant: 'Xin chào! Mình là nhân viên tư vấn của ").append(shop.getName())
              .append(". Mình có thể giúp gì cho bạn hôm nay? Bạn đang tìm kiếm sản phẩm gì vậy?'\n\n");
        
        // Ví dụ 2: Hỏi về sản phẩm bán
        prompt.append("Example 2 - Shop Products Question:\n");
        prompt.append("Customer: 'Shop bán gì vậy?'\n");
        prompt.append("Assistant: 'Dạ shop ").append(shop.getName()).append(" chuyên bán các sản phẩm ");
        if (categories != null && !categories.isEmpty()) {
            for (int i = 0; i < Math.min(3, categories.size()); i++) {
                if (i > 0) prompt.append(", ");
                prompt.append(categories.get(i));
            }
            if (categories.size() > 3) prompt.append(" và nhiều mặt hàng khác");
        } else {
            prompt.append("[liệt kê cụ thể các danh mục sản phẩm]");
        }
        prompt.append(" ạ. Bạn đang quan tâm đến loại sản phẩm nào?'\n\n");
        
        // Ví dụ 3: Hỏi về lịch sử hội thoại
        prompt.append("Example 3 - Conversation History Question:\n");
        prompt.append("Customer: 'Tôi hỏi bạn những gì rồi?'\n");
        prompt.append("Assistant: 'Dạ vừa rồi bạn đã hỏi shop mình bán những gì, và mình đã chia sẻ là shop chuyên về các sản phẩm [categories]. Bạn có quan tâm đến sản phẩm cụ thể nào không ạ?'\n\n");
        
        // Ví dụ 4: Tìm kiếm sản phẩm
        prompt.append("Example 4 - Product Search:\n");
        prompt.append("Customer: 'Tôi muốn mua áo khoác'\n");
        prompt.append("Assistant: 'Dạ shop mình có nhiều mẫu áo khoác đẹp lắm ạ. Bạn thích phong cách nào? Áo khoác dù, áo khoác da, hay áo khoác len ấm? Mình có thể tư vấn cụ thể hơn cho bạn.'\n\n");
          // Ví dụ 5: Đặt hàng THIẾU địa chỉ (CRITICAL EXAMPLE)
        prompt.append("Example 5 - Order WITHOUT Address (CRITICAL - Customer address is missing):\n");
        prompt.append("Customer: 'Tôi muốn mua 2 áo phông size L' (and customer address in database is 'Đang cập nhật')\n");
        prompt.append("Response: {\n");
        prompt.append("  \"response_text\": \"Dạ vâng, mình hiểu bạn muốn đặt 2 áo phông size L. Để hoàn tất đơn hàng, bạn vui lòng cho mình biết địa chỉ giao hàng là gì nhé?\",\n");
        prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
        prompt.append("  \"action_required\": false,\n");
        prompt.append("  \"missing_information\": [\"delivery_address\"]\n");
        prompt.append("}\n\n");
        
        // Ví dụ 6: Đặt hàng có địa chỉ
        prompt.append("Example 6 - Order with Address:\n");
        prompt.append("Customer: 'Tôi muốn mua 2 áo phông size L giao đến 25 Nguyễn Thị Minh Khai, Q.1, TP.HCM'\n");
        prompt.append("Response: {\n");
        prompt.append("  \"response_text\": \"Dạ vâng, mình đã xác nhận đơn hàng 2 áo phông size L. Shop sẽ giao hàng đến địa chỉ 25 Nguyễn Thị Minh Khai, Quận 1, TP.HCM cho bạn nhé!\",\n");
        prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
        prompt.append("  \"extracted_address\": \"25 Nguyễn Thị Minh Khai, Quận 1, TP.HCM\",\n");
        prompt.append("  \"action_required\": true,\n");
        prompt.append("  \"action_details\": { \"action_type\": \"PLACEORDER\", \"product_id\": 123, \"quantity\": 2 }\n");
        prompt.append("}\n\n");
        
        // System requirements for response format
        prompt.append("RESPONSE FORMAT (Must be valid JSON):\n");
        prompt.append("Your response must be in valid JSON format with these fields:\n");
        prompt.append("1. 'response_text': Your conversational response to customer (ALWAYS REQUIRED)\n");
        prompt.append("2. 'detected_intent': The intent category\n");
        prompt.append("3. 'action_required': Boolean indicating if system action is needed\n");
        prompt.append("4. 'action_details': JSON object with details if action_required is true\n");
        prompt.append("5. 'extracted_address': Any address mentioned by the customer (IMPORTANT FOR ORDERS)\n");
        prompt.append("6. 'extracted_phone': Any phone number mentioned by the customer\n");
        prompt.append("7. 'missing_information': Array of missing data needed\n");
        prompt.append("8. 'follow_up_questions': Array of suggested questions\n");
        prompt.append("9. 'create_order': Boolean indicating if an order should be created (for ADDRESS_RESPONSE intent)\n\n");
        
        // Customer message
        prompt.append("CURRENT CUSTOMER MESSAGE: \"").append(message).append("\"\n");
        
        return prompt.toString();
    }
    
    /**
     * Build a prompt specifically for product recommendations
     */
    private String buildProductRecommendationPrompt(Shop shop, List<ProductResponse> products, String query) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are a product recommendation system for '").append(shop.getName()).append("' shop.\n");
        prompt.append("Based on the customer query and available products, recommend the most relevant items.\n\n");
        
        prompt.append("Available products:\n");
        for (ProductResponse product : products) {
            prompt.append("- ID: ").append(product.getId())
                  .append(", Name: ").append(product.getName())
                  .append(", Price: ").append(product.getPrice())
                  .append(", Category: ").append(product.getCategory())
                  .append(", Description: ").append(product.getDescription())
                  .append(", Stock: ").append(product.getStock())
                  .append("\n");
        }
        
        prompt.append("\nCustomer query: \"").append(query).append("\"\n\n");
        
        prompt.append("IMPORTANT: Keep your recommendation_text short, natural and conversational. ")
              .append("Instead of lengthy formal responses, use casual friendly language as if you're talking to a friend.\n\n");
        
        prompt.append("Provide the following in JSON format:\n")
              .append("1. 'matched_products': Array of matched product IDs in order of relevance\n")
              .append("2. 'recommendation_text': Brief, friendly text explaining the recommendations\n")
              .append("3. 'confidence_score': Number between 0-1 indicating confidence in the match\n");
        
        return prompt.toString();
    }
    
    /**
     * Build a prompt for order processing
     */
    private String buildOrderProcessingPrompt(Shop shop, Customer customer, String orderRequest) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are processing an order for '").append(shop.getName()).append("' shop.\n\n");
        
        prompt.append("Customer information:\n")
              .append("- Name: ").append(customer.getFullname()).append("\n")
              .append("- Address: ").append(customer.getAddress()).append("\n")
              .append("- Phone: ").append(customer.getPhone()).append("\n")
              .append("- Email: ").append(customer.getEmail()).append("\n\n");
        
        prompt.append("Order request: \"").append(orderRequest).append("\"\n\n");
        
        prompt.append("IMPORTANT: Be concise and focused only on extracting order information. ")
              .append("Don't generate any lengthy explanations or justifications.\n\n");
        
        prompt.append("Analyze the order request and provide in JSON format:\n")
              .append("1. 'product_ids': Array of product IDs mentioned in the order\n")
              .append("2. 'quantities': Array of quantities for each product (in same order as product_ids)\n")
              .append("3. 'special_instructions': Any special instructions for the order\n")
              .append("4. 'delivery_preference': Any delivery preferences specified\n")
              .append("5. 'is_valid_order': Boolean indicating if this is a valid order request\n")
              .append("6. 'missing_details': Array of any missing information needed\n");
        
        return prompt.toString();
    }
    
    /**
     * Build a prompt for validating delivery information
     */
    private String buildDeliveryInfoValidationPrompt(String customerInput) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("You are validating delivery information for an order.\n\n");
        prompt.append("Customer input: \"").append(customerInput).append("\"\n\n");
        
        prompt.append("Extract and validate the following information in JSON format:\n")
              .append("1. 'extracted_name': Customer name extracted from input\n")
              .append("2. 'extracted_phone': Phone number extracted from input\n")
              .append("3. 'extracted_address': Delivery address extracted from input\n")
              .append("4. 'is_name_valid': Boolean indicating if the name is valid\n")
              .append("5. 'is_phone_valid': Boolean indicating if the phone number is valid\n")
              .append("6. 'is_address_valid': Boolean indicating if the address is valid\n")
              .append("7. 'missing_fields': Array of required fields that are missing\n")
              .append("8. 'suggested_corrections': Any suggested corrections for invalid fields\n");
        
        return prompt.toString();
    }
    
    /**
     * Call Gemini API with structured format for general customer messages
     */
    private String callGeminiWithStructuredFormat(String prompt) {
        try {
            String apiUrl = String.format("%s/%s:generateContent?key=%s", 
                geminiApiUrl, geminiChatModel, geminiApiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request body with structured response schema for general conversations
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);
            
            // Define the response schema
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            generationConfig.put("temperature", 0.4); // Tăng temperature để phản hồi sáng tạo, tự nhiên hơn
            generationConfig.put("maxOutputTokens", 2048); // Cho phép trả lời dài hơn khi cần thiết
            
            ObjectNode responseSchema = generationConfig.putObject("response_schema");
            responseSchema.put("type", "OBJECT");
            
            // Define required fields in the response schema
            ArrayNode requiredFields = objectMapper.createArrayNode();
            requiredFields.add("response_text");
            requiredFields.add("detected_intent");
            requiredFields.add("action_required");
            responseSchema.set("required", requiredFields);
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            // Response text - Always required
            ObjectNode responseText = properties.putObject("response_text");
            responseText.put("type", "STRING");
            responseText.put("description", "Human-like conversational response in Vietnamese (unless customer used English). Be specific, enthusiastic and helpful, never vague.");
            
            // Detected intent - Use standardized values
            ObjectNode detectedIntent = properties.putObject("detected_intent");
            detectedIntent.put("type", "STRING");
            detectedIntent.put("description", "The standardized intent detected from the user message");
            ArrayNode allowedIntents = objectMapper.createArrayNode()
                .add("GREETING")
                .add("GETPRODUCT")
                .add("SEARCHPRODUCT")
                .add("ADDTOCART")
                .add("PLACEORDER")
                .add("CHECKORDER")
                .add("CANCELORDER")
                .add("SENDIMAGE")
                .add("SHOWPRODUCT")
                .add("CONVERSATION_REFERENCE")
                .add("ADDRESS_RESPONSE")
                .add("GENERAL_QUERY");
            detectedIntent.set("enum", allowedIntents);
            
            ObjectNode actionRequired = properties.putObject("action_required");
            actionRequired.put("type", "BOOLEAN");
            actionRequired.put("description", "Whether the system needs to take an action based on this message");
            
            // Add extracted address property
            ObjectNode extractedAddress = properties.putObject("extracted_address");
            extractedAddress.put("type", "STRING");
            extractedAddress.put("description", "Any delivery address mentioned by the customer, in full detail");
            
            // Add extracted phone property
            ObjectNode extractedPhone = properties.putObject("extracted_phone");
            extractedPhone.put("type", "STRING");
            extractedPhone.put("description", "Any phone number mentioned by the customer");
            
            // Define action_details with properties
            ObjectNode actionDetails = properties.putObject("action_details");
            actionDetails.put("type", "OBJECT");
            actionDetails.put("description", "Details about the required action, if any");
            
            // Define properties for action_details
            ObjectNode actionDetailsProperties = actionDetails.putObject("properties");
            
            // Add action_type property with allowed values
            ObjectNode actionType = actionDetailsProperties.putObject("action_type");
            actionType.put("type", "STRING");
            actionType.put("description", "The standardized action code");
            ArrayNode allowedActions = objectMapper.createArrayNode()
                .add("GETPRODUCT")
                .add("SEARCHPRODUCT")
                .add("ADDTOCART")
                .add("PLACEORDER")
                .add("CHECKORDER")
                .add("CANCELORDER")
                .add("SENDIMAGE")
                .add("SHOWPRODUCT");
            actionType.set("enum", allowedActions);
            
            // Add product_id property
            ObjectNode productId = actionDetailsProperties.putObject("product_id");
            productId.put("type", "NUMBER");
            productId.put("description", "Product ID for order or product details");
            
            // Add product_name property
            ObjectNode productName = actionDetailsProperties.putObject("product_name");
            productName.put("type", "STRING");
            productName.put("description", "Product name for reference");
            
            // Add quantity property
            ObjectNode quantity = actionDetailsProperties.putObject("quantity");
            quantity.put("type", "NUMBER");
            quantity.put("description", "Quantity for orders");
            
            // Add note property
            ObjectNode note = actionDetailsProperties.putObject("note");
            note.put("type", "STRING");
            note.put("description", "Special instructions or notes for orders");
            
            // Add search_keywords property
            ObjectNode searchKeywords = actionDetailsProperties.putObject("search_keywords");
            searchKeywords.put("type", "STRING");
            searchKeywords.put("description", "Keywords for product search");
            
            // Add order_id property
            ObjectNode orderId = actionDetailsProperties.putObject("order_id");
            orderId.put("type", "STRING");
            orderId.put("description", "Order ID when referencing a specific order, especially for CANCELORDER intent. Omit for cancelling most recent order.");
            
            // Add send_product_images property
            ObjectNode sendProductImages = actionDetailsProperties.putObject("send_product_images");
            sendProductImages.put("type", "BOOLEAN");
            sendProductImages.put("description", "Whether to send product images directly to customer");
            
            // Add product_ids_for_images property
            ObjectNode productIdsForImages = actionDetailsProperties.putObject("product_ids_for_images");
            productIdsForImages.put("type", "ARRAY");
            productIdsForImages.put("description", "List of product IDs to send images for");
            ObjectNode imageIdItems = productIdsForImages.putObject("items");
            imageIdItems.put("type", "NUMBER");
            
            // Required fields for action_details when it's included
            if (actionDetails.has("properties") && actionDetails.get("properties").size() > 0) {
                ObjectNode actionDetailsSchema = actionDetails;
                ArrayNode actionRequiredFields = objectMapper.createArrayNode();
                actionRequiredFields.add("action_type");
                actionDetailsSchema.set("required", actionRequiredFields);
            }
            
            ObjectNode missingInfo = properties.putObject("missing_information");
            missingInfo.put("type", "ARRAY");
            missingInfo.put("description", "List of missing information needed to complete the action");
            ObjectNode missingItems = missingInfo.putObject("items");
            missingItems.put("type", "STRING");
            
            // Add follow-up questions array property
            ObjectNode followUpQuestions = properties.putObject("follow_up_questions");
            followUpQuestions.put("type", "ARRAY");
            followUpQuestions.put("description", "Suggested follow-up questions to maintain conversation flow");
            ObjectNode followUpItems = followUpQuestions.putObject("items");
            followUpItems.put("type", "STRING");
            
            // Convert request body to string for sending
            String requestBodyString = objectMapper.writeValueAsString(requestBody);
            
            // Log API request for debugging
            if (log.isDebugEnabled()) {
                log.debug("Sending request to Gemini API: {}", requestBodyString);
            }
            
            // Send request to Gemini API
            HttpEntity<String> entity = new HttpEntity<>(requestBodyString, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Log API response for debugging
            if (log.isDebugEnabled()) {
                log.debug("Received response from Gemini API: {}", response.getBody());
            }
            
            // Extract the structured JSON response from Gemini
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode candidates = responseJson.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content1 = candidates.get(0).path("content");
                JsonNode parts1 = content1.path("parts");
                
                if (parts1.isArray() && parts1.size() > 0) {
                    String result = parts1.get(0).path("text").asText();
                    // Log the parsed result
                    if (log.isDebugEnabled()) {
                        log.debug("Parsed result from Gemini: {}", result);
                    }
                    
                    // Verify the response has the required response_text field
                    try {
                        JsonNode resultJson = objectMapper.readTree(result);
                        if (!resultJson.has("response_text") || resultJson.get("response_text").asText().isEmpty()) {
                            log.warn("AI response missing required response_text field, adding default response");
                            ((ObjectNode)resultJson).put("response_text", "Xin lỗi, tôi không hiểu rõ yêu cầu của bạn. Bạn có thể giải thích rõ hơn được không?");
                            return objectMapper.writeValueAsString(resultJson);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing AI response: {}", e.getMessage());
                    }
                    
                    return result;
                }
            }
            
            return createErrorResponse("Unable to parse AI response");
            
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            return createErrorResponse("Error generating AI response: " + e.getMessage());
        }
    }
    
    /**
     * Call Gemini API for product recommendations with structured format
     */
    private String callGeminiForProductRecommendations(String prompt) {
        try {
            String apiUrl = String.format("%s/%s:generateContent?key=%s", 
                geminiApiUrl, geminiChatModel, geminiApiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request body with response schema for product recommendations
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);
            
            // Define the response schema
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            
            ObjectNode responseSchema = generationConfig.putObject("response_schema");
            responseSchema.put("type", "OBJECT");
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            // Define matched_products array
            ObjectNode matchedProducts = properties.putObject("matched_products");
            matchedProducts.put("type", "ARRAY");
            ObjectNode idItems = matchedProducts.putObject("items");
            idItems.put("type", "NUMBER");
            
            // Define recommendation_text
            ObjectNode recommendationText = properties.putObject("recommendation_text");
            recommendationText.put("type", "STRING");
            
            // Define confidence_score
            ObjectNode confidenceScore = properties.putObject("confidence_score");
            confidenceScore.put("type", "NUMBER");
            
            // Send request to Gemini API
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Extract the structured JSON response from Gemini
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode candidates = responseJson.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content1 = candidates.get(0).path("content");
                JsonNode parts1 = content1.path("parts");
                
                if (parts1.isArray() && parts1.size() > 0) {
                    return parts1.get(0).path("text").asText();
                }
            }
            
            return createErrorResponse("Unable to parse product recommendations");
            
        } catch (Exception e) {
            log.error("Error calling Gemini API for product recommendations: {}", e.getMessage(), e);
            return createErrorResponse("Error generating product recommendations: " + e.getMessage());
        }
    }
    
    /**
     * Call Gemini API for order processing with structured format
     */
    private String callGeminiForOrderProcessing(String prompt) {
        try {
            String apiUrl = String.format("%s/%s:generateContent?key=%s", 
                geminiApiUrl, geminiChatModel, geminiApiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request body with response schema for order processing
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);
            
            // Define the response schema
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            
            ObjectNode responseSchema = generationConfig.putObject("response_schema");
            responseSchema.put("type", "OBJECT");
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            // Define product_ids array
            ObjectNode productIds = properties.putObject("product_ids");
            productIds.put("type", "ARRAY");
            ObjectNode idItems = productIds.putObject("items");
            idItems.put("type", "NUMBER");
            
            // Define quantities array
            ObjectNode quantities = properties.putObject("quantities");
            quantities.put("type", "ARRAY");
            ObjectNode quantityItems = quantities.putObject("items");
            quantityItems.put("type", "NUMBER");
            
            // Define special_instructions
            ObjectNode specialInstructions = properties.putObject("special_instructions");
            specialInstructions.put("type", "STRING");
            
            // Define delivery_preference
            ObjectNode deliveryPreference = properties.putObject("delivery_preference");
            deliveryPreference.put("type", "STRING");
            
            // Define is_valid_order
            ObjectNode isValidOrder = properties.putObject("is_valid_order");
            isValidOrder.put("type", "BOOLEAN");
            
            // Define missing_details
            ObjectNode missingDetails = properties.putObject("missing_details");
            missingDetails.put("type", "ARRAY");
            ObjectNode missingItems = missingDetails.putObject("items");
            missingItems.put("type", "STRING");
            
            // Send request to Gemini API
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Extract the structured JSON response from Gemini
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode candidates = responseJson.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content1 = candidates.get(0).path("content");
                JsonNode parts1 = content1.path("parts");
                
                if (parts1.isArray() && parts1.size() > 0) {
                    return parts1.get(0).path("text").asText();
                }
            }
            
            return createErrorResponse("Unable to parse order processing response");
            
        } catch (Exception e) {
            log.error("Error calling Gemini API for order processing: {}", e.getMessage(), e);
            return createErrorResponse("Error processing order: " + e.getMessage());
        }
    }
    
    /**
     * Call Gemini API for delivery information validation
     */
    private String callGeminiForDeliveryValidation(String prompt) {
        try {
            String apiUrl = String.format("%s/%s:generateContent?key=%s", 
                geminiApiUrl, geminiChatModel, geminiApiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request body with response schema for delivery validation
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);
            
            // Define the response schema
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            
            ObjectNode responseSchema = generationConfig.putObject("response_schema");
            responseSchema.put("type", "OBJECT");
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            // Define extracted fields
            ObjectNode extractedName = properties.putObject("extracted_name");
            extractedName.put("type", "STRING");
            
            ObjectNode extractedPhone = properties.putObject("extracted_phone");
            extractedPhone.put("type", "STRING");
            
            ObjectNode extractedAddress = properties.putObject("extracted_address");
            extractedAddress.put("type", "STRING");
            
            // Define validation fields
            ObjectNode isNameValid = properties.putObject("is_name_valid");
            isNameValid.put("type", "BOOLEAN");
            
            ObjectNode isPhoneValid = properties.putObject("is_phone_valid");
            isPhoneValid.put("type", "BOOLEAN");
            
            ObjectNode isAddressValid = properties.putObject("is_address_valid");
            isAddressValid.put("type", "BOOLEAN");
            
            // Define missing fields
            ObjectNode missingFields = properties.putObject("missing_fields");
            missingFields.put("type", "ARRAY");
            ObjectNode missingItems = missingFields.putObject("items");
            missingItems.put("type", "STRING");
            
            // Define suggested corrections
            ObjectNode suggestedCorrections = properties.putObject("suggested_corrections");
            suggestedCorrections.put("type", "OBJECT");
            
            // Define properties for suggested_corrections
            ObjectNode correctionsProperties = suggestedCorrections.putObject("properties");
            
            // Add name correction property
            ObjectNode nameCorrection = correctionsProperties.putObject("name");
            nameCorrection.put("type", "STRING");
            
            // Add phone correction property
            ObjectNode phoneCorrection = correctionsProperties.putObject("phone");
            phoneCorrection.put("type", "STRING");
            
            // Add address correction property
            ObjectNode addressCorrection = correctionsProperties.putObject("address");
            addressCorrection.put("type", "STRING");
            
            // Send request to Gemini API
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Extract the structured JSON response from Gemini
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode candidates = responseJson.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content1 = candidates.get(0).path("content");
                JsonNode parts1 = content1.path("parts");
                
                if (parts1.isArray() && parts1.size() > 0) {
                    return parts1.get(0).path("text").asText();
                }
            }
            
            return createErrorResponse("Unable to parse delivery validation response");
            
        } catch (Exception e) {
            log.error("Error calling Gemini API for delivery validation: {}", e.getMessage(), e);
            return createErrorResponse("Error validating delivery information: " + e.getMessage());
        }
    }
    
    /**
     * Create a standard error response in JSON format
     */
    private String createErrorResponse(String errorMessage) {
        try {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", true);
            errorResponse.put("message", errorMessage);
            return objectMapper.writeValueAsString(errorResponse);
        } catch (Exception e) {
            log.error("Error creating error response: {}", e.getMessage(), e);
            return "{\"error\":true,\"message\":\"Unknown error occurred\"}";
        }
    }

    /**
     * Analyze the intent of a customer message without loading shop context
     */
    private String analyzeMessageIntent(String message, List<ConversationHistoryService.ConversationEntry> history) {
        try {
            String apiUrl = String.format("%s/%s:generateContent?key=%s", 
                geminiApiUrl, geminiChatModel, geminiApiKey);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create a lightweight request body for intent analysis
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            
            StringBuilder prompt = new StringBuilder();
            
            // Check if the last assistant message was asking for address
            boolean lastMessageWasAddressRequest = false;
            String previousAssistantMessage = "";
            
            if (history != null && history.size() > 1) {
                // Look at the most recent assistant message
                for (int i = history.size() - 2; i >= 0; i--) {
                    ConversationHistoryService.ConversationEntry entry = history.get(i);
                    if ("assistant".equals(entry.role)) {
                        previousAssistantMessage = entry.message.toLowerCase();
                        if (previousAssistantMessage.contains("địa chỉ") || 
                            previousAssistantMessage.contains("giao đến đâu") || 
                            previousAssistantMessage.contains("giao hàng") || 
                            previousAssistantMessage.contains("gửi đến đâu")) {
                            lastMessageWasAddressRequest = true;
                        }
                        break;
                    }
                }
            }
            
            // Thêm lịch sử hội thoại vào phân tích intent
            if (history != null && !history.isEmpty()) {
                prompt.append("Recent conversation history:\n");
                // Lấy tối đa 5 tin nhắn gần nhất để phân tích
                int startIndex = Math.max(0, history.size() - 5);
                for (int i = startIndex; i < history.size(); i++) {
                    ConversationHistoryService.ConversationEntry entry = history.get(i);
                    if ("customer".equals(entry.role)) {
                        prompt.append("Customer: ").append(entry.message).append("\n");
                    } else {
                        prompt.append("Assistant: ").append(entry.message).append("\n");
                    }
                }
                prompt.append("\n");
            }
            
            prompt.append("You are a shop assistant. Analyze this customer message in context of any previous conversation: \"")
                  .append(message).append("\"\n\n");
                  
            prompt.append("Determine the intent and whether shop product context is needed to properly respond.\n\n");
            
            // Add specific instruction for address responses
            if (lastMessageWasAddressRequest) {
                prompt.append("IMPORTANT: If this message is ONLY providing an address in response to a previous question, ");
                prompt.append("set the detected_intent to 'ADDRESS_RESPONSE' instead of 'PLACEORDER'. ");
                prompt.append("When the intent is ADDRESS_RESPONSE, set action_required to true and create_order to true.\n\n");
                prompt.append("If you previously asked the customer for an address for an order, include these order details in the response:\n");
                prompt.append("1. Set create_order: true\n");
                prompt.append("2. Include full action_details with action_type: 'PLACEORDER', product_id and quantity\n");
                prompt.append("3. Extract any product mention and quantities from previous messages\n\n");
            }
            
            prompt.append("IMPORTANT: Standard action codes to use:\n");
            prompt.append("- GREETING: Simple greeting or welcome message\n");
            prompt.append("- GETPRODUCT: Get detailed information about a specific product\n");
            prompt.append("- SEARCHPRODUCT: Search for products matching keywords\n");
            prompt.append("- ADDTOCART: Add a product to the customer's cart\n");
            prompt.append("- PLACEORDER: Complete the order process\n");
            prompt.append("- CHECKORDER: Check order status\n");
            prompt.append("- CANCELORDER: Cancel an existing order\n");
            prompt.append("- SENDIMAGE: Send product images directly to customer\n");
            prompt.append("- SHOWPRODUCT: Show product details with image\n");
            prompt.append("- CONVERSATION_REFERENCE: Customer is asking about previous conversation\n");
            prompt.append("- ADDRESS_RESPONSE: Customer is providing their address in response to a request\n");
            prompt.append("- GENERAL_QUERY: General question not fitting other categories\n\n");
            
            // Add specific instructions for ORDER CANCELLATION intent detection
            prompt.append("IMPORTANT CANCELORDER DETECTION GUIDELINES:\n");
            prompt.append("1. Use CANCELORDER intent when messages include phrases like:\n");
            prompt.append("   - 'hủy đơn hàng', 'hủy đơn', 'không mua nữa'\n");
            prompt.append("   - 'huỷ giao dịch', 'không đặt hàng nữa', 'không muốn mua nữa'\n");
            prompt.append("   - 'cancel order', 'cancel my order', 'don't want to buy anymore'\n");
            prompt.append("2. If the customer mentions a specific order ID ('đơn hàng số X', 'mã đơn hàng X'):\n");
            prompt.append("   - Include the 'order_id' field with the exact order number\n");
            prompt.append("3. If no order ID is mentioned, do NOT include the 'order_id' field\n");
            prompt.append("4. Set action_required: true for all cancellation requests\n");
            prompt.append("5. Always provide a clear confirmation in response_text\n\n");
              // Add specific instructions for ADDRESS_RESPONSE intent detection
            prompt.append("IMPORTANT ADDRESS_RESPONSE GUIDELINES:\n");
            prompt.append("1. When customer is providing ONLY an address after you requested it:\n");
            prompt.append("   - Set detected_intent: 'ADDRESS_RESPONSE'\n");
            prompt.append("   - Set action_required: true\n");
            prompt.append("   - Set create_order: true if this address is for an order\n");
            prompt.append("   - Include extracted_address with the full address\n");
            prompt.append("   - CRITICAL: If this address is for an order and you previously asked for it, ALWAYS include action_details with:\n");
            prompt.append("     * action_type: 'PLACEORDER'\n");
            prompt.append("     * product_id: Extract from previous conversation (find the product the customer wanted to buy)\n");
            prompt.append("     * quantity: Extract from previous conversation (find the quantity the customer wanted)\n");
            prompt.append("2. Look through the conversation history to find what product and quantity the customer wanted to order\n");
            prompt.append("3. Examples of address-only responses:\n");
            prompt.append("   - 'Địa chỉ của tôi là 123 Đường ABC'\n");
            prompt.append("   - 'Giao đến 72 Hương An, Huế nhé'\n");
            prompt.append("   - 'Nhà mình ở số 45 đường Trần Hưng Đạo'\n\n");
            
            prompt.append("IMPORTANT: When customer asks about product images or product details, always prefer to use:\n");
            prompt.append("IMPORTANT: When customer asks about product images or product details, always prefer to use:\n");
            prompt.append("- SENDIMAGE: For sending multiple product images directly\n");
            prompt.append("- SHOWPRODUCT: For showing detailed product information with image\n\n");
            
            prompt.append("IMPORTANT: When customer asks about previous messages or what they asked before, use CONVERSATION_REFERENCE\n\n");
            
            prompt.append("IMPORTANT: Always look for and extract delivery information when customer may provide it:\n");
            prompt.append("1. Look for addresses: Any text related to addresses, delivery locations, house numbers, streets, etc.\n");
            prompt.append("2. Look for phone numbers: Vietnamese phone numbers in formats like 0912345678, +84912345678, etc.\n");
            prompt.append("3. Include the extracted information in extracted_address and extracted_phone fields.\n\n");
            
            prompt.append("IMPORTANT: Keep your response_text short, natural, conversational, and human-like. ");
            prompt.append("Always respond in Vietnamese unless the customer uses English. ");
            prompt.append("Act like a friendly shop assistant, not an AI. ");
            prompt.append("Use casual language with appropriate Vietnamese expressions. ");
            prompt.append("For simple greetings like 'hello', 'hi', etc., just respond with a friendly greeting. ");
            prompt.append("Avoid lengthy, formal responses.\n\n");
            
            part.put("text", prompt.toString());
            
            // Define the response schema
            ObjectNode generationConfig = requestBody.putObject("generationConfig");
            generationConfig.put("response_mime_type", "application/json");
            generationConfig.put("temperature", 0.3); // Tăng temperature cho trả lời sáng tạo hơn
            
            ObjectNode responseSchema = generationConfig.putObject("response_schema");
            responseSchema.put("type", "OBJECT");
            
            // Define required fields in the response schema
            ArrayNode requiredFields = objectMapper.createArrayNode();
            requiredFields.add("response_text");
            requiredFields.add("detected_intent");
            requiredFields.add("needs_shop_context");
            responseSchema.set("required", requiredFields);
            
            ObjectNode properties = responseSchema.putObject("properties");
            
            ObjectNode responseText = properties.putObject("response_text");
            responseText.put("type", "STRING");
            
            ObjectNode detectedIntent = properties.putObject("detected_intent");
            detectedIntent.put("type", "STRING");
            ArrayNode allowedIntents = objectMapper.createArrayNode()
                .add("GREETING")
                .add("GETPRODUCT")
                .add("SEARCHPRODUCT")
                .add("ADDTOCART")
                .add("PLACEORDER")
                .add("CHECKORDER")
                .add("CANCELORDER")
                .add("SENDIMAGE")
                .add("SHOWPRODUCT")
                .add("CONVERSATION_REFERENCE")
                .add("ADDRESS_RESPONSE")
                .add("GENERAL_QUERY");
            detectedIntent.set("enum", allowedIntents);
            
            ObjectNode needsShopContext = properties.putObject("needs_shop_context");
            needsShopContext.put("type", "BOOLEAN");
            
            ObjectNode actionRequired = properties.putObject("action_required");
            actionRequired.put("type", "BOOLEAN");
            
            // Add extracted_address property
            ObjectNode extractedAddress = properties.putObject("extracted_address");
            extractedAddress.put("type", "STRING");
            extractedAddress.put("description", "Address extracted from the customer message, if any");
            
            // Add extracted_phone property
            ObjectNode extractedPhone = properties.putObject("extracted_phone");
            extractedPhone.put("type", "STRING");
            extractedPhone.put("description", "Phone number extracted from the customer message, if any");
            
            // Add follow-up questions array property
            ObjectNode followUpQuestions = properties.putObject("follow_up_questions");
            followUpQuestions.put("type", "ARRAY");
            followUpQuestions.put("description", "Suggested follow-up questions to maintain conversation flow");
            ObjectNode followUpItems = followUpQuestions.putObject("items");
            followUpItems.put("type", "STRING");
            
            // Convert request body to string for sending
            String requestBodyString = objectMapper.writeValueAsString(requestBody);
            
            // Log API request for debugging
            if (log.isDebugEnabled()) {
                log.debug("Sending intent analysis request to Gemini API: {}", requestBodyString);
            }
            
            // Send request to Gemini API
            HttpEntity<String> entity = new HttpEntity<>(requestBodyString, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            // Log API response for debugging
            if (log.isDebugEnabled()) {
                log.debug("Received intent analysis response from Gemini API: {}", response.getBody());
            }
            
            // Extract the structured JSON response from Gemini
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            JsonNode candidates = responseJson.path("candidates");
            
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode content1 = candidates.get(0).path("content");
                JsonNode parts1 = content1.path("parts");
                
                if (parts1.isArray() && parts1.size() > 0) {
                    String result = parts1.get(0).path("text").asText();
                    // Log the parsed result
                    if (log.isDebugEnabled()) {
                        log.debug("Parsed intent analysis result from Gemini: {}", result);
                    }
                    return result;
                }
            }
            
            return createErrorResponse("Unable to parse AI response");
            
        } catch (Exception e) {
            log.error("Error analyzing message intent: {}", e.getMessage(), e);
            return createErrorResponse("Error analyzing message: " + e.getMessage());
        }
    }
    
    /**
     * Checks if the message is likely just providing an address in response to a request
     */
    private boolean isProbablyAddressOnly(String message, List<ConversationHistoryService.ConversationEntry> history) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        
        // Most address-only messages are short and contain location indicators
        boolean messageContainsAddressIndicators = message.contains("số") || 
                                                 message.contains("đường") || 
                                                 message.contains("phố") || 
                                                 message.contains("quận") || 
                                                 message.contains("phường") || 
                                                 message.contains("tỉnh") || 
                                                 message.contains("thành phố") || 
                                                 message.contains("tp") || 
                                                 message.contains("huyện") || 
                                                 message.contains("xã");
        
        // Check if there are product mentions or quantity indicators that would suggest an order
        boolean containsOrderLanguage = message.toLowerCase().contains("đặt") || 
                                      message.toLowerCase().contains("mua") || 
                                      message.toLowerCase().contains("order") || 
                                      message.matches(".*\\d+\\s*(cái|lon|chai|hộp|gói|kg|gram|g).*");
        
        // If no order language but has address indicators, it might be address-only
        if (messageContainsAddressIndicators && !containsOrderLanguage) {
            return true;
        }
        
        // Check if previous assistant message was asking for address
        if (history != null && history.size() > 1) {
            for (int i = history.size() - 2; i >= 0; i--) {
                ConversationHistoryService.ConversationEntry entry = history.get(i);
                if ("assistant".equals(entry.role)) {
                    String previousMessage = entry.message.toLowerCase();
                    if (previousMessage.contains("địa chỉ") || 
                        previousMessage.contains("giao đến đâu") || 
                        previousMessage.contains("giao hàng") || 
                        previousMessage.contains("gửi đến đâu")) {
                        return true;
                    }
                    break;
                }
            }
        }
        
        return false;
    }

    /**
     * Get product by ID for a specific shop
     * 
     * @param shopId The ID of the shop
     * @param productId The ID of the product
     * @return The product if found, null otherwise
     */
    @Override
    public Product getProductById(Long shopId, Long productId) {
        try {
            return productRepository.findByIdAndShopId(productId, shopId)
                .orElse(null);
        } catch (Exception e) {
            log.error("Error retrieving product with ID {} for shop {}: {}", 
                productId, shopId, e.getMessage(), e);
            return null;
        }
    }

    // Cải thiện xử lý lịch sử hội thoại
    private String formatConversationHistory(List<ConversationHistoryService.ConversationEntry> history) {
        if (history.isEmpty()) {
            return "";
        }
        
        StringBuilder historyStr = new StringBuilder();
        historyStr.append("CONVERSATION HISTORY (IMPORTANT - PLEASE READ CAREFULLY):\n");
        
        // Add clear messaging about current session
        historyStr.append("THIS IS THE CURRENT SESSION ONLY. PREVIOUS SESSIONS ARE NOT RELEVANT.\n");
        
        // Group messages by timestamp if from the same day
        LocalDateTime lastTimestamp = null;
        String lastSession = "";
        
        for (int i = 0; i < history.size(); i++) {
            ConversationHistoryService.ConversationEntry entry = history.get(i);
            String role = "customer".equals(entry.role) ? "Customer" : "You (Assistant)";
            
            // Check if this is a new session day
            if (lastTimestamp == null || 
                !entry.timestamp.toLocalDate().equals(lastTimestamp.toLocalDate())) {
                String sessionDay = entry.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!sessionDay.equals(lastSession)) {
                    historyStr.append("\n--- Session: ").append(sessionDay).append(" ---\n");
                    lastSession = sessionDay;
                }
            }
            
            // Format with timestamp
            String timeStr = entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm"));
            historyStr.append(i+1).append(". ").append(role).append(" [").append(timeStr).append("]: ").append(entry.message).append("\n");
            
            lastTimestamp = entry.timestamp;
        }
        
        historyStr.append("\nIMPORTANT INSTRUCTIONS FOR HANDLING CONVERSATION HISTORY:\n");
        historyStr.append("- Reference ONLY messages from the CURRENT SESSION\n");
        historyStr.append("- DO NOT reference or acknowledge conversations from previous sessions\n");
        historyStr.append("- If the customer asks what they've asked before, ONLY mention questions from the current session\n");
        historyStr.append("- If asked about previous conversation, summarize ONLY the current session\n");
        historyStr.append("- Remember customer preferences and questions already asked in this session\n");
        historyStr.append("- Maintain consistent information throughout the conversation\n");
        historyStr.append("- If unsure about a previous message, ask for clarification\n\n");
        
        return historyStr.toString();
    }

    /**
     * Reset the conversation history for a customer if needed
     * For example, when a new session begins or on explicit reset request
     * 
     * @param shopId The shop ID
     * @param customerId The customer ID
     */
    public void resetConversation(Long shopId, String customerId) {
        conversationHistoryService.clearHistory(shopId, customerId);
        log.info("Reset conversation history for shop ID: {}, customer ID: {}", shopId, customerId);
    }

    /**
     * Check if customer address is missing when trying to place an order
     * 
     * @param customer Customer entity to check
     * @return Response requesting address, or null if address is available
     */
    private String checkMissingAddressForOrder(Customer customer) {
        try {
            // Check if address is missing or placeholder
            if (customer.getAddress() == null || 
                customer.getAddress().isEmpty() ||
                customer.getAddress().equals("Đang cập nhật")) {
                
                // Generate a response asking for the address
                String responseText = "Để hoàn tất đơn hàng của bạn, mình cần địa chỉ giao hàng. "
                    + "Bạn vui lòng cho biết địa chỉ giao hàng là gì nhé?";
                
                // Create a structured response JSON
                ObjectNode infoRequestResponse = objectMapper.createObjectNode();
                infoRequestResponse.put("response_text", responseText);
                infoRequestResponse.put("detected_intent", "ADDRESS_REQUEST");
                infoRequestResponse.put("needs_shop_context", false);
                infoRequestResponse.put("action_required", false);
                
                // Add the missing field to the response
                ArrayNode missingFieldsNode = infoRequestResponse.putArray("missing_fields");
                missingFieldsNode.add("address");
                
                // Log what we're doing
                log.info("Requesting address for order placement, customer ID: {}", customer.getId());
                
                // Save this message to conversation history
                conversationHistoryService.addMessage(
                    customer.getShop().getId(), 
                    customer.getPhone(), 
                    "assistant", 
                    responseText
                );
                
                return infoRequestResponse.toString();
            }
            
            return null; // Address is available, continue with normal processing
        } catch (Exception e) {
            log.error("Error checking missing customer address: {}", e.getMessage(), e);
            return null; // Continue with normal processing if this fails
        }
    }

    /**
     * Save an order based on AI conversation
     * @param customerId Customer ID
     * @param productId Product ID
     * @param quantity Quantity of the product to order
     * @return The created OrderDTO
     */
    private OrderDTO saveOrderFromAI(Long customerId, Long productId, int quantity) {
        CreateOrderRequest orderRequest = CreateOrderRequest.builder()
                .customerId(customerId)
                .productId(productId)
                .quantity(quantity)
                .note("Đơn hàng được tạo qua AI Assistant")
                .deliveryUnit("Chưa xác định")
                .build();
                
        // Call the order service to create the order
        OrderDTO createdOrder = orderService.createOrder(orderRequest);
        return createdOrder;
    }

    /**
     * Extract address information from the customer message
     * @param message The customer message
     * @return Extracted address or null if not found
     */
    private String extractAddressFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // Look for common Vietnamese address patterns
        // Pattern 1: Anything following "địa chỉ", "address", "giao hàng đến", etc.
        String lowerMessage = message.toLowerCase();
        String[] addressIndicators = {"địa chỉ", "address", "giao hàng đến", "giao đến", "gửi đến", "gửi hàng đến", "ship đến", "nhà mình", "chỗ mình"};
        
        for (String indicator : addressIndicators) {
            int index = lowerMessage.indexOf(indicator);
            if (index >= 0) {
                // Extract from the indicator to the end or next punctuation
                String addressPart = message.substring(index + indicator.length()).trim();
                
                // Remove leading punctuation like ":", "là", etc.
                addressPart = addressPart.replaceAll("^[\\s:,;là]*", "").trim();
                
                // Take until the end of the sentence or next paragraph
                int endIndex = -1;
                for (String endMark : new String[]{".", "\n", "\r"}) {
                    int tempIndex = addressPart.indexOf(endMark);
                    if (tempIndex > 0 && (endIndex == -1 || tempIndex < endIndex)) {
                        endIndex = tempIndex;
                    }
                }
                
                if (endIndex > 0) {
                    addressPart = addressPart.substring(0, endIndex).trim();
                }
                
                if (!addressPart.isEmpty() && addressPart.length() >= 5) {
                    return addressPart;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extract phone number from the customer message
     * @param message The customer message
     * @return Extracted phone number or null if not found
     */
    private String extractPhoneFromMessage(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        
        // Regular expression for Vietnamese phone numbers
        // Pattern covers formats like: 0912345678, 84912345678, +84912345678
        String phonePattern = "\\b((\\+84|84|0)\\d{9,10})\\b";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(phonePattern);
        java.util.regex.Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            String phone = matcher.group(1);
            
            // Normalize phone format to start with 0
            if (phone.startsWith("+84")) {
                phone = "0" + phone.substring(3);
            } else if (phone.startsWith("84")) {
                phone = "0" + phone.substring(2);
            }
            
            return phone;
        }
        
        return null;
    }

    /**
     * Cancel an order based on AI conversation
     * @param orderId Order ID to cancel
     * @return The cancelled order DTO
     */
    private OrderDTO cancelOrderFromAI(Long orderId) {
        try {
            // Create a request to update the order status to CANCELLED
            UpdateOrderStatusRequest request = UpdateOrderStatusRequest.builder()
                    .status(OrderStatus.CANCELLED)
                    .build();
                    
            // Call the order service to update the order status
            OrderDTO cancelledOrder = orderService.updateOrderStatus(orderId, request);
            return cancelledOrder;
        } catch (Exception e) {
            log.error("Error cancelling order from AI: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Find the most recent order ID for a customer
     * @param customerId Customer ID
     * @return The most recent order ID or null if none found
     */
    private Long findRecentOrderId(Long customerId) {
        try {
            // Get the most recent orders for this customer
            List<OrderDTO> customerOrders = orderService.getOrdersByCustomerId(customerId);
            
            if (customerOrders != null && !customerOrders.isEmpty()) {
                // Sort by created date in descending order (most recent first)
                customerOrders.sort((o1, o2) -> {
                    LocalDateTime date1 = o1.getCreatedAt();
                    LocalDateTime date2 = o2.getCreatedAt();
                    return date2.compareTo(date1); // Descending order
                });
                
                // Return the ID of the most recent order that's not already cancelled
                for (OrderDTO order : customerOrders) {
                    if (order.getStatus() != OrderStatus.CANCELLED) {
                        return order.getId();
                    }
                }
            }
            
            return null; // No valid orders found
        } catch (Exception e) {
            log.error("Error finding recent order for customer: {}", e.getMessage(), e);
            return null;
        }
    }
} 