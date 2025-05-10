package com.g18.assistant.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.g18.assistant.entity.Product;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;
import com.g18.assistant.repository.TelegramMessageRepository;
import com.g18.assistant.service.ShopAIService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.service.OrderService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ShopTelegramBot extends TelegramLongPollingBot {
    
    private final Shop shop;
    private final TelegramMessageRepository messageRepository;
    private final ShopAIService shopAIService;
    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    
    @Getter
    private boolean isRunning = false;
    
    // Add these fields to the class for pending orders
    private final Map<String, PendingOrder> pendingOrders = new ConcurrentHashMap<>();
    
    // Inner class to store pending order details
    private static class PendingOrder {
        Long productId;
        Integer quantity;
        String note;
        Long customerId;
        
        public PendingOrder(Long productId, Integer quantity, String note, Long customerId) {
            this.productId = productId;
            this.quantity = quantity;
            this.note = note;
            this.customerId = customerId;
        }
    }
    
    public ShopTelegramBot(String botToken, Shop shop, TelegramMessageRepository messageRepository, 
                           ShopAIService shopAIService, ObjectMapper objectMapper,
                           CustomerRepository customerRepository, OrderService orderService) {
        super(botToken);
        this.shop = shop;
        this.messageRepository = messageRepository;
        this.shopAIService = shopAIService;
        this.objectMapper = objectMapper;
        this.customerRepository = customerRepository;
        this.orderService = orderService;
    }
    
    @Override
    public String getBotUsername() {
        return "Shop_" + shop.getId() + "_Bot";
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            log.info("Received message from Telegram - Shop ID: {}, Chat ID: {}", 
                    shop.getId(), update.getMessage().getChatId());
            
            Long chatId = update.getMessage().getChatId();
            String userId = update.getMessage().getFrom().getId().toString();
            String username = update.getMessage().getFrom().getUserName() != null ? 
                    update.getMessage().getFrom().getUserName() : 
                    update.getMessage().getFrom().getFirstName();
            
            String messageText = "";
            String fileUrl = null;
            String fileType = null;
            
            // Extract message text
            if (update.getMessage().hasText()) {
                messageText = update.getMessage().getText();
            }
            
            // Check if this is an address command
            if (messageText.startsWith("/address ")) {
                String address = messageText.substring(9).trim(); // Extract address after "/address "
                if (!address.isEmpty()) {
                    // Process address update
                    processAddressUpdate(userId, chatId, address);
                    return; // Skip AI processing for address commands
                }
            }
            
            // Extract file info if present
            if (update.getMessage().hasDocument()) {
                Document document = update.getMessage().getDocument();
                fileUrl = document.getFileId();
                fileType = document.getMimeType();
                if (messageText.isEmpty()) {
                    messageText = "[Document: " + document.getFileName() + "]";
                }
            } else if (update.getMessage().hasPhoto()) {
                List<PhotoSize> photos = update.getMessage().getPhoto();
                // Get the largest photo
                PhotoSize photo = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .orElse(null);
                if (photo != null) {
                    fileUrl = photo.getFileId();
                    fileType = "image/jpeg";
                    if (messageText.isEmpty()) {
                        messageText = "[Photo]";
                    }
                }
            }
            
            // Store message in database
            TelegramMessage telegramMessage = TelegramMessage.builder()
                    .shop(shop)
                    .userId(userId)
                    .username(username)
                    .messageText(messageText)
                    .chatId(chatId)
                    .fileUrl(fileUrl)
                    .fileType(fileType)
                    .receivedAt(LocalDateTime.now())
                    .build();
            
            messageRepository.save(telegramMessage);
            
            log.info("Stored Telegram message - Shop ID: {}, Message: {}", 
                    shop.getId(), messageText);

            // Process message with Gemini AI
            try {
                // Call AI service to get response
                String aiResponse = shopAIService.processCustomerMessage(shop.getId(), userId, username, messageText);
                
                // Parse the AI response
                JsonNode responseJson = objectMapper.readTree(aiResponse);
                
                // Check if there was an error
                if (responseJson.has("error") && responseJson.get("error").asBoolean()) {
                    log.error("AI error: {}", responseJson.get("message").asText());
                    sendTextMessage(chatId, "I'm sorry, I'm having trouble understanding your request right now. Please try again later.");
                    return;
                }
                
                // Extract the human-readable response text
                String responseText = responseJson.has("response_text") ? 
                        responseJson.get("response_text").asText() : 
                        "Thank you for your message. I'll get back to you soon.";
                
                // First send the text response to the user
                sendTextMessage(chatId, responseText);
                
                // Log detected intent for monitoring
                if (responseJson.has("detected_intent")) {
                    String intent = responseJson.get("detected_intent").asText();
                    log.info("AI detected intent for Shop {}, User {}: {}", 
                            shop.getId(), userId, intent);
                }
                
                // Handle any actions that need to be performed
                if (responseJson.has("action_required") && responseJson.get("action_required").asBoolean()) {
                    log.info("AI indicates action required for Shop {}, User {}", 
                            shop.getId(), userId);
                    
                    JsonNode actionDetails = responseJson.path("action_details");
                    
                    // Check if we need to send product images
                    if (actionDetails.has("send_product_images") && 
                        actionDetails.get("send_product_images").asBoolean() && 
                        actionDetails.has("product_ids_for_images")) {
                        
                        // Get product IDs to send images for
                        JsonNode productIds = actionDetails.path("product_ids_for_images");
                        if (productIds.isArray()) {
                            for (JsonNode idNode : productIds) {
                                try {
                                    Long productId = idNode.asLong();
                                    // Send product image
                                    sendProductImage(chatId, productId);
                                } catch (Exception e) {
                                    log.error("Error sending product image: {}", e.getMessage(), e);
                                }
                            }
                        }
                    }
                    
                    // Handle SHOWPRODUCT action type
                    if (actionDetails.has("action_type") && 
                        "SHOWPRODUCT".equals(actionDetails.get("action_type").asText()) && 
                        actionDetails.has("product_id")) {
                        
                        try {
                            Long productId = actionDetails.get("product_id").asLong();
                            sendProductDetails(chatId, productId);
                        } catch (Exception e) {
                            log.error("Error showing product details: {}", e.getMessage(), e);
                        }
                    }
                    
                    // Handle PLACEORDER action type
                    if (actionDetails.has("action_type") && 
                        "PLACEORDER".equals(actionDetails.get("action_type").asText())) {
                        
                        try {
                            // Lấy chi tiết đơn hàng từ phản hồi AI
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
                            
                            // Tìm hoặc tạo thông tin khách hàng
                            processPlaceOrder(userId, chatId, productId, quantity, note);
                        } catch (Exception e) {
                            log.error("Error processing order: {}", e.getMessage(), e);
                            sendTextMessage(chatId, "Xin lỗi, có lỗi xảy ra khi xử lý đơn hàng của bạn. Vui lòng thử lại sau.");
                        }
                    }
                    
                    // In a real implementation, we would act on the other action_details
                    // For example, creating orders, checking status, etc.
                }
                
            } catch (Exception e) {
                log.error("Error processing message with AI: {}", e.getMessage(), e);
                sendTextMessage(chatId, "I'm sorry, I couldn't process your request. Please try again later.");
            }
        }
    }
    
    /**
     * Send a text message to a specific chat
     * 
     * @param chatId The chat ID to send the message to
     * @param text The text to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendTextMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            execute(message);
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send message to chat {} for shop {}: {}", 
                    chatId, shop.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Start the bot
     * 
     * @param botsApi The Telegram bots API instance
     * @return true if the bot was registered successfully
     */
    public boolean start(TelegramBotsApi botsApi) {
        try {
            botsApi.registerBot(this);
            isRunning = true;
            log.info("Started Telegram bot for shop ID: {}", shop.getId());
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to start Telegram bot for shop {}: {}", 
                    shop.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop the bot
     */
    public void stop() {
        isRunning = false;
        log.info("Stopped Telegram bot for shop ID: {}", shop.getId());
    }

    /**
     * Send a product image to a specific chat
     * 
     * @param chatId The chat ID to send the message to
     * @param productId The product ID to send the image for
     * @return true if the image was sent successfully, false otherwise
     */
    private boolean sendProductImage(Long chatId, Long productId) {
        try {
            // Get product details from the database
            Product product = shopAIService.getProductById(shop.getId(), productId);
            if (product == null) {
                sendTextMessage(chatId, "I'm sorry, I couldn't find that product.");
                return false;
            }
            
            // If the product has no image, send a message
            if (product.getImageBase64() == null || product.getImageBase64().isEmpty()) {
                sendTextMessage(chatId, "I'm sorry, this product doesn't have an image.");
                return true;
            }
            
            // Create a photo message with caption
            SendPhoto photoMessage = new SendPhoto();
            photoMessage.setChatId(chatId);
            
            // Convert base64 to input file
            byte[] imageBytes = java.util.Base64.getDecoder().decode(product.getImageBase64());
            InputFile inputFile = new InputFile(
                new java.io.ByteArrayInputStream(imageBytes), 
                product.getName() + ".jpg"
            );
            photoMessage.setPhoto(inputFile);
            photoMessage.setCaption(product.getName() + " - " + product.getPrice() + " VND");
            
            // Send the photo
            execute(photoMessage);
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send product image to chat {} for shop {}: {}", 
                    chatId, shop.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error retrieving product image: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Send detailed product information with image to a specific chat
     * 
     * @param chatId The chat ID to send the message to
     * @param productId The product ID to send details for
     * @return true if the details were sent successfully, false otherwise
     */
    private boolean sendProductDetails(Long chatId, Long productId) {
        try {
            // Get product details from the database
            Product product = shopAIService.getProductById(shop.getId(), productId);
            if (product == null) {
                sendTextMessage(chatId, "I'm sorry, I couldn't find that product.");
                return false;
            }
            
            // Create a detailed product description
            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("*").append(product.getName()).append("*\n\n");
            detailsBuilder.append("💰 Giá: ").append(product.getPrice()).append(" VND\n");
            detailsBuilder.append("🏷️ Danh mục: ").append(product.getCategory()).append("\n");
            if (product.getStock() > 0) {
                detailsBuilder.append("✅ Còn hàng: ").append(product.getStock()).append(" sản phẩm\n");
            } else {
                detailsBuilder.append("❌ Hết hàng\n");
            }
            detailsBuilder.append("\n📝 Mô tả: ").append(product.getDescription());
            
            String detailsText = detailsBuilder.toString();
            
            // If the product has an image, send with image
            if (product.getImageBase64() != null && !product.getImageBase64().isEmpty()) {
                SendPhoto photoMessage = new SendPhoto();
                photoMessage.setChatId(chatId);
                
                // Convert base64 to input file
                byte[] imageBytes = java.util.Base64.getDecoder().decode(product.getImageBase64());
                InputFile inputFile = new InputFile(
                    new java.io.ByteArrayInputStream(imageBytes), 
                    product.getName() + ".jpg"
                );
                photoMessage.setPhoto(inputFile);
                photoMessage.setCaption(detailsText);
                photoMessage.setParseMode("Markdown");
                
                // Send the photo with details
                execute(photoMessage);
            } else {
                // If no image, just send text details
                SendMessage message = new SendMessage();
                message.setChatId(chatId);
                message.setText(detailsText);
                message.setParseMode("Markdown");
                execute(message);
            }
            
            return true;
        } catch (TelegramApiException e) {
            log.error("Failed to send product details to chat {} for shop {}: {}", 
                    chatId, shop.getId(), e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Error retrieving product details: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Xử lý đặt hàng
     * 
     * @param userId ID người dùng Telegram
     * @param chatId ID chat Telegram
     * @param productId ID sản phẩm
     * @param quantity Số lượng
     * @param note Ghi chú
     */
    private void processPlaceOrder(String userId, Long chatId, Long productId, Integer quantity, String note) {
        try {
            // Mặc định số lượng là 1 nếu không được chỉ định
            if (quantity == null) {
                quantity = 1;
            }
            
            // Tìm khách hàng trong hệ thống dựa trên userId Telegram
            Customer customer = customerRepository.findByPhoneAndShopId(userId, shop.getId())
                .orElseGet(() -> {
                    // Nếu không tìm thấy, tạo khách hàng mới với địa chỉ tạm thời
                    Customer newCustomer = new Customer();
                    newCustomer.setPhone(userId);
                    newCustomer.setShop(shop);
                    newCustomer.setFullname("Telegram User " + userId);
                    // Đặt một địa chỉ tạm thời để tránh lỗi null
                    newCustomer.setAddress("Đang cập nhật");
                    newCustomer.setEmail("telegram_" + userId + "@example.com");
                    return customerRepository.save(newCustomer);
                });
            
            // Nếu không có productId, thông báo lỗi
            if (productId == null) {
                sendTextMessage(chatId, "Xin lỗi, không tìm thấy thông tin sản phẩm trong đơn hàng.");
                return;
            }
            
            // Lấy thông tin sản phẩm để hiển thị trong tin nhắn
            Product product = shopAIService.getProductById(shop.getId(), productId);
            if (product == null) {
                sendTextMessage(chatId, "Xin lỗi, không tìm thấy sản phẩm với ID: " + productId);
                return;
            }
            
            // Kiểm tra xem khách hàng đã có địa chỉ thật chưa hay vẫn đang dùng địa chỉ mặc định
            if ("Đang cập nhật".equals(customer.getAddress())) {
                // Lưu thông tin đơn hàng chờ để xử lý sau khi có địa chỉ
                pendingOrders.put(userId, new PendingOrder(productId, quantity, note, customer.getId()));
                
                // Gửi thông báo yêu cầu cung cấp địa chỉ - cách tiếp cận tự nhiên hơn
                sendTextMessage(chatId, 
                    "Tuyệt vời! Mình đã nhận được đơn đặt " + quantity + " " + product.getName() + " của bạn. " +
                    "Để shop có thể giao hàng, bạn cho mình xin địa chỉ nhận hàng được không ạ?");
                
                return;
            }
            
            // Tạo yêu cầu đặt hàng
            CreateOrderRequest orderRequest = new CreateOrderRequest();
            orderRequest.setCustomerId(customer.getId());
            orderRequest.setProductId(productId);
            orderRequest.setQuantity(quantity);
            orderRequest.setNote(note);
            
            // Gọi service để tạo đơn hàng
            OrderDTO createdOrder = orderService.createOrder(orderRequest);
            
            // Gửi xác nhận đơn hàng
            String confirmationMessage = String.format(
                "✅ Đơn hàng của bạn đã được tạo thành công!\n\n" +
                "🔢 Mã đơn hàng: #%d\n" +
                "🛍️ Sản phẩm: %s\n" +
                "🔢 Số lượng: %d\n" +
                "🏷️ Trạng thái: %s\n\n" +
                "📦 Địa chỉ giao hàng: %s\n\n" +
                "Cảm ơn bạn đã mua hàng tại %s!",
                createdOrder.getId(),
                createdOrder.getProductName(),
                createdOrder.getQuantity(),
                createdOrder.getStatus().toString(),
                customer.getAddress(),
                shop.getName()
            );
            
            sendTextMessage(chatId, confirmationMessage);
            
        } catch (Exception e) {
            log.error("Error creating order: {}", e.getMessage(), e);
            sendTextMessage(chatId, "Có lỗi xảy ra khi tạo đơn hàng: " + e.getMessage());
        }
    }
    
    /**
     * Xử lý cập nhật địa chỉ khách hàng và hoàn tất đơn hàng đang chờ
     * 
     * @param userId ID người dùng Telegram
     * @param chatId ID chat Telegram
     * @param address Địa chỉ mới của khách hàng
     */
    private void processAddressUpdate(String userId, Long chatId, String address) {
        try {
            // Tìm khách hàng để cập nhật địa chỉ
            Customer customer = customerRepository.findByPhoneAndShopId(userId, shop.getId())
                .orElse(null);
                
            if (customer == null) {
                sendTextMessage(chatId, "Xin lỗi, không tìm thấy thông tin khách hàng của bạn.");
                return;
            }
            
            // Cập nhật địa chỉ khách hàng
            customer.setAddress(address);
            customerRepository.save(customer);
            
            sendTextMessage(chatId, "✅ Đã cập nhật địa chỉ giao hàng thành công!");
            
            // Kiểm tra xem có đơn hàng đang chờ không
            PendingOrder pendingOrder = pendingOrders.remove(userId);
            if (pendingOrder != null) {
                // Tạo đơn hàng với địa chỉ mới
                CreateOrderRequest orderRequest = new CreateOrderRequest();
                orderRequest.setCustomerId(pendingOrder.customerId);
                orderRequest.setProductId(pendingOrder.productId);
                orderRequest.setQuantity(pendingOrder.quantity);
                orderRequest.setNote(pendingOrder.note);
                
                // Gọi service để tạo đơn hàng
                OrderDTO createdOrder = orderService.createOrder(orderRequest);
                
                // Gửi xác nhận đơn hàng
                String confirmationMessage = String.format(
                    "✅ Đơn hàng của bạn đã được tạo thành công!\n\n" +
                    "🔢 Mã đơn hàng: #%d\n" +
                    "🛍️ Sản phẩm: %s\n" +
                    "🔢 Số lượng: %d\n" +
                    "🏷️ Trạng thái: %s\n" +
                    "🏠 Địa chỉ giao hàng: %s\n\n" +
                    "Cảm ơn bạn đã mua hàng tại %s!",
                    createdOrder.getId(),
                    createdOrder.getProductName(),
                    createdOrder.getQuantity(),
                    createdOrder.getStatus().toString(),
                    address,
                    shop.getName()
                );
                
                sendTextMessage(chatId, confirmationMessage);
            }
        } catch (Exception e) {
            log.error("Error updating address: {}", e.getMessage(), e);
            sendTextMessage(chatId, "Có lỗi xảy ra khi cập nhật địa chỉ: " + e.getMessage());
        }
    }
} 