package com.g18.assistant.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.repository.TelegramMessageRepository;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.service.ShopAIService;
import com.g18.assistant.service.ShopService;
import com.g18.assistant.service.OrderService;
import com.g18.assistant.service.PendingOrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manager class responsible for creating, starting, stopping and tracking all Telegram bots
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotManager {
      private final ShopService shopService;
    private final TelegramMessageRepository messageRepository;
    private final ShopAIService shopAIService;
    private final ObjectMapper objectMapper;
    private final CustomerRepository customerRepository;
    private final OrderService orderService;
    private final PendingOrderService pendingOrderService;
    
    private TelegramBotsApi telegramBotsApi;
    private final Map<Long, ShopTelegramBot> activeBots = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        try {
            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            log.info("Initialized Telegram Bot Manager");
        } catch (TelegramApiException e) {
            log.error("Failed to initialize Telegram Bot API: {}", e.getMessage());
        }
    }
    
    @PreDestroy
    public void cleanup() {
        // Stop all active bots
        for (Map.Entry<Long, ShopTelegramBot> entry : activeBots.entrySet()) {
            entry.getValue().stop();
            log.info("Stopped Telegram bot for shop ID: {}", entry.getKey());
        }
        activeBots.clear();
        log.info("Cleaned up Telegram Bot Manager");
    }
    
    /**
     * Start a Telegram bot for a shop
     * 
     * @param shop The shop
     * @param token The Telegram bot token
     * @return true if the bot was started successfully, false otherwise
     */
    public boolean startBot(Shop shop, String token) {
        // Check if bot is already running
        if (activeBots.containsKey(shop.getId())) {
            log.info("Telegram bot for shop ID {} is already running", shop.getId());
            return true;
        }
        
        try {            // Create and register the bot with the new dependencies
            ShopTelegramBot bot = new ShopTelegramBot(
                token, 
                shop, 
                messageRepository, 
                shopAIService, 
                objectMapper,
                customerRepository,
                orderService,
                pendingOrderService
            );
            boolean success = bot.start(telegramBotsApi);
            
            if (success) {
                activeBots.put(shop.getId(), bot);
                log.info("Started Telegram bot for shop ID: {}", shop.getId());
                return true;
            } else {
                log.error("Failed to start Telegram bot for shop ID: {}", shop.getId());
                return false;
            }
        } catch (Exception e) {
            log.error("Error starting Telegram bot for shop {}: {}", shop.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Stop a Telegram bot for a shop
     * 
     * @param shopId The shop ID
     * @return true if the bot was stopped successfully, false if the bot was not running
     */
    public boolean stopBot(Long shopId) {
        ShopTelegramBot bot = activeBots.remove(shopId);
        if (bot != null) {
            bot.stop();
            log.info("Stopped Telegram bot for shop ID: {}", shopId);
            return true;
        } else {
            log.info("No active Telegram bot found for shop ID: {}", shopId);
            return false;
        }
    }
    
    /**
     * Check if a bot is running for a shop
     * 
     * @param shopId The shop ID
     * @return true if a bot is running for the shop, false otherwise
     */
    public boolean isBotRunning(Long shopId) {
        ShopTelegramBot bot = activeBots.get(shopId);
        return bot != null && bot.isRunning();
    }
    
    /**
     * Send a message to a Telegram chat via a specific shop's bot
     * 
     * @param shopId The shop ID
     * @param chatId The chat ID to send the message to
     * @param text The text to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessage(Long shopId, Long chatId, String text) {
        ShopTelegramBot bot = activeBots.get(shopId);
        if (bot != null && bot.isRunning()) {
            return bot.sendTextMessage(chatId, text);
        } else {
            log.error("Cannot send message: No active bot for shop ID: {}", shopId);
            return false;
        }
    }
} 