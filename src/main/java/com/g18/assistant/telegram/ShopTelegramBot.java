package com.g18.assistant.telegram;

import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;
import com.g18.assistant.repository.TelegramMessageRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Slf4j
public class ShopTelegramBot extends TelegramLongPollingBot {
    
    private final Shop shop;
    private final TelegramMessageRepository messageRepository;
    
    @Getter
    private boolean isRunning = false;
    
    public ShopTelegramBot(String botToken, Shop shop, TelegramMessageRepository messageRepository) {
        super(botToken);
        this.shop = shop;
        this.messageRepository = messageRepository;
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
            
            // Send acknowledgment
            sendTextMessage(chatId, "✅ Message received. Thank you!");
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
} 