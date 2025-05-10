package com.g18.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Product;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.ConversationHistoryService;
import com.g18.assistant.service.CustomerService;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopAIServiceImpl implements ShopAIService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final ShopService shopService;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final ConversationHistoryService conversationHistoryService;
    private final CustomerService customerService;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;
    
    @Value("${app.gemini.chat-model-id}")
    private String geminiChatModel;
    
    @Value("${app.gemini.api-url}")
    private String geminiApiUrl;
    
    @Override
    public String processCustomerMessage(Long shopId, String customerId, String customerName, String message) {
        try {
            // Extract potentially useful customer information from the message
            Map<String, String> extractedInfo = customerService.extractCustomerInfoFromMessage(message);
            
            // Find or create a customer record
            Customer customer = null;
            
            // Try to find by the given customer ID (phone number)
            if (customerId != null && !customerId.isEmpty()) {
                customer = customerService.findByPhoneAndShopId(customerId, shopId);
                
                // If not found, try by email
                if (customer == null && extractedInfo.containsKey("email")) {
                    customer = customerService.findByEmailAndShopId(extractedInfo.get("email"), shopId);
                }
                
                // If still not found, create a new customer
                if (customer == null) {
                    customer = customerService.createNewCustomer(
                        shopId, 
                        customerId, 
                        customerName,
                        extractedInfo.getOrDefault("email", null)
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

            // First, analyze the intent without loading all shop data
            String intentAnalysis = analyzeMessageIntent(message, history);
            JsonNode analysisJson = objectMapper.readTree(intentAnalysis);
            if (analysisJson.has("error") && analysisJson.get("error").asBoolean()) {
                return intentAnalysis;
            }
            
            String detectedIntent = analysisJson.path("detected_intent").asText("GENERAL_QUERY");
            
            // Only check for missing address when the customer is trying to place an order
            if ("PLACEORDER".equals(detectedIntent) && customer != null) {
                String addressCheckResponse = checkMissingAddressForOrder(customer);
                if (addressCheckResponse != null) {
                    return addressCheckResponse;
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
            // Lưu phản hồi AI vào lịch sử
            try {
                JsonNode aiJson = objectMapper.readTree(aiResponse);
                String respText = aiJson.has("response_text") ? aiJson.get("response_text").asText() : aiResponse;
                conversationHistoryService.addMessage(shopId, customerId, "assistant", respText);
            } catch (Exception ex) {
                conversationHistoryService.addMessage(shopId, customerId, "assistant", aiResponse);
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
    public String processOrderRequest(Long shopId, String customerId, String orderRequest) {
        try {
            // Get shop information without user validation (for bots)
            Shop shop = shopService.getShopByIdForBotServices(shopId);
            
            // Verify customer exists
            Customer customer = null;
            if (customerId != null && !customerId.isEmpty()) {
                // Try to find by phone (assuming customerId might be a phone number in messaging apps)
                Optional<Customer> customerOpt = customerRepository.findByPhoneAndShopId(customerId, shopId);
                if (customerOpt.isEmpty()) {
                    // Try to find by email as a fallback
                    customerOpt = customerRepository.findByEmailAndShopId(customerId, shopId);
                }
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
            prompt.append("This is a returning customer. Use their name naturally and reference their previous interactions if relevant.\n\n");
        } else {
            prompt.append("NEW CUSTOMER:\n");
            prompt.append("Name: '").append(customerName).append("'\n");
            prompt.append("This is a new customer. Be extra welcoming and take initiative to understand their needs.\n\n");
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
        prompt.append("CUSTOMER INFORMATION DETECTION (IMPORTANT):\n");
        prompt.append("1. Look for and detect customer details in messages, including:\n");
        prompt.append("   - Địa chỉ (Addresses): Any text mentioning địa chỉ, nơi ở, chỗ ở, etc.\n");
        prompt.append("   - Họ tên (Full names): Vietnamese full names when customer introduces themselves\n");
        prompt.append("   - Số điện thoại (Phone numbers): Vietnamese phone numbers starting with 0, +84, 84\n");
        prompt.append("   - Email: Any email addresses shared in the conversation\n");
        prompt.append("2. When customer provides this information, acknowledge it naturally\n");
        prompt.append("3. If a customer is placing an order and hasn't provided their address yet, ask for it\n");
        prompt.append("4. If you detect customer information, continue the conversation normally\n");
        
        // Thay thế phần ORDER PROCESSING GUIDELINES cũ
        prompt.append("ORDER PROCESSING GUIDELINES (CRITICAL - FOLLOW EXACTLY):\n");
        prompt.append("When handling PLACEORDER intent (customer wants to buy something):\n");
        prompt.append("1. ALWAYS include these fields in action_details:\n");
        prompt.append("   - product_id: The exact numeric ID of the product\n");
        prompt.append("   - quantity: The exact numeric quantity (default to 1 if unspecified)\n");
        prompt.append("   - action_type: Must be 'PLACEORDER'\n");
        prompt.append("2. For products mentioned by name, match to a specific product_id from available products\n");
        prompt.append("3. If multiple products are available, choose the most relevant one\n");
        prompt.append("4. If product ID cannot be determined, put action_required: false\n");
        prompt.append("5. Always confirm the order details in response_text\n\n");
        
        // Thêm ví dụ cho Cocacola
        prompt.append("Example - Specific Order (CRITICAL):\n");
        if (!products.isEmpty()) {
            ProductResponse exampleProduct = products.get(0);
            prompt.append(String.format("If customer says: 'Mua 2 %s'\n", exampleProduct.getName()));
            prompt.append("You MUST respond with:\n");
            prompt.append("{\n");
            prompt.append(String.format("  \"response_text\": \"Vâng, mình xác nhận đơn hàng 2 %s. Shop sẽ liên hệ bạn sớm!\",\n", exampleProduct.getName()));
            prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
            prompt.append("  \"action_required\": true,\n");
            prompt.append("  \"action_details\": {\n");
            prompt.append("    \"action_type\": \"PLACEORDER\",\n");
            prompt.append(String.format("    \"product_id\": %d,\n", exampleProduct.getId()));
            prompt.append("    \"quantity\": 2\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
        } else {
            prompt.append("If customer says: 'Mua 2 chai nước'\n");
            prompt.append("You MUST respond with:\n");
            prompt.append("{\n");
            prompt.append("  \"response_text\": \"Vâng, mình xác nhận đơn hàng 2 chai nước. Shop sẽ liên hệ bạn sớm!\",\n");
            prompt.append("  \"detected_intent\": \"PLACEORDER\",\n");
            prompt.append("  \"action_required\": true,\n");
            prompt.append("  \"action_details\": {\n");
            prompt.append("    \"action_type\": \"PLACEORDER\",\n");
            prompt.append("    \"product_id\": [ID of the most relevant product],\n");
            prompt.append("    \"quantity\": 2\n");
            prompt.append("  }\n");
            prompt.append("}\n\n");
        }
        
        // Thêm danh sách sản phẩm cho việc tra cứu ID
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
        prompt.append("- GENERAL_QUERY: General question not fitting other categories\n\n");
        
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
        
        // System requirements for response format
        prompt.append("RESPONSE FORMAT (Must be valid JSON):\n");
        prompt.append("Your response must be in valid JSON format with these fields:\n");
        prompt.append("1. 'response_text': Your conversational response to customer (ALWAYS REQUIRED)\n");
        prompt.append("2. 'detected_intent': The intent category\n");
        prompt.append("3. 'action_required': Boolean indicating if system action is needed\n");
        prompt.append("4. 'action_details': JSON object with details if action_required is true\n");
        prompt.append("5. 'missing_information': Array of missing data needed\n");
        prompt.append("6. 'follow_up_questions': Array of suggested questions\n\n");
        
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
                .add("GENERAL_QUERY");
            detectedIntent.set("enum", allowedIntents);
            
            ObjectNode actionRequired = properties.putObject("action_required");
            actionRequired.put("type", "BOOLEAN");
            actionRequired.put("description", "Whether the system needs to take an action based on this message");
            
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
            orderId.put("description", "Order ID when referencing a specific order");
            
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
            prompt.append("- GENERAL_QUERY: General question not fitting other categories\n\n");
            
            prompt.append("IMPORTANT: When the customer asks to see product images or product details, always prefer to use:\n");
            prompt.append("- SENDIMAGE: For sending multiple product images directly\n");
            prompt.append("- SHOWPRODUCT: For showing detailed product information with image\n\n");
            
            prompt.append("IMPORTANT: When customer asks about previous messages or what they asked before, use CONVERSATION_REFERENCE\n\n");
            
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
                .add("GENERAL_QUERY");
            detectedIntent.set("enum", allowedIntents);
            
            ObjectNode needsShopContext = properties.putObject("needs_shop_context");
            needsShopContext.put("type", "BOOLEAN");
            
            ObjectNode actionRequired = properties.putObject("action_required");
            actionRequired.put("type", "BOOLEAN");
            
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
} 