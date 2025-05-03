package com.g18.assistant.service.impl;

import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;
import com.g18.assistant.repository.AccessTokenRepository;
import com.g18.assistant.repository.TelegramMessageRepository;
import com.g18.assistant.service.ShopService;
import com.g18.assistant.service.TelegramBotService;
import com.g18.assistant.telegram.TelegramBotManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramBotServiceImpl implements TelegramBotService {
    
    private final ShopService shopService;
    private final AccessTokenRepository accessTokenRepository;
    private final TelegramMessageRepository telegramMessageRepository;
    private final TelegramBotManager telegramBotManager;
    
    @Override
    @Transactional
    public boolean startBot(Long shopId, String username) {
        try {
            // Validate shop ownership
            Shop shop = shopService.validateUserShop(shopId, username);
            
            // Check if bot is already running
            if (telegramBotManager.isBotRunning(shopId)) {
                log.info("Telegram bot for shop ID {} is already running", shopId);
                return true;
            }
            
            // Find active Telegram token for the shop
            Optional<AccessToken> tokenOpt = accessTokenRepository.findByShopAndMethodAndStatus(
                    shop, AccessToken.TokenMethod.TELEGRAM, AccessToken.TokenStatus.ACTIVE);
            
            if (tokenOpt.isEmpty()) {
                log.error("No active Telegram token found for shop ID: {}", shopId);
                return false;
            }
            
            // Start the bot
            boolean success = telegramBotManager.startBot(shop, tokenOpt.get().getAccessToken());
            
            if (success) {
                log.info("Successfully started Telegram bot for shop ID: {}", shopId);
            } else {
                log.error("Failed to start Telegram bot for shop ID: {}", shopId);
            }
            
            return success;
        } catch (Exception e) {
            log.error("Error starting Telegram bot for shop {}: {}", shopId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean stopBot(Long shopId, String username) {
        try {
            // Validate shop ownership
            shopService.validateUserShop(shopId, username);
            
            // Stop the bot
            return telegramBotManager.stopBot(shopId);
        } catch (Exception e) {
            log.error("Error stopping Telegram bot for shop {}: {}", shopId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean getBotStatus(Long shopId, String username) {
        try {
            // Validate shop ownership
            shopService.validateUserShop(shopId, username);
            
            // Check if bot is running
            return telegramBotManager.isBotRunning(shopId);
        } catch (Exception e) {
            log.error("Error checking Telegram bot status for shop {}: {}", shopId, e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<TelegramMessage> getRecentMessages(Long shopId, String username, int limit) {
        try {
            // Validate shop ownership
            Shop shop = shopService.validateUserShop(shopId, username);
            
            // Limit to a reasonable number
            int actualLimit = Math.min(limit, 100);
            
            // Get recent messages
            return telegramMessageRepository.findByShopOrderByReceivedAtDesc(shop)
                    .stream()
                    .limit(actualLimit)
                    .toList();
        } catch (Exception e) {
            log.error("Error retrieving messages for shop {}: {}", shopId, e.getMessage());
            return Collections.emptyList();
        }
    }
    
    @Override
    public boolean sendMessage(Long shopId, String username, Long chatId, String message) {
        try {
            // Validate shop ownership
            shopService.validateUserShop(shopId, username);
            
            // Send the message
            return telegramBotManager.sendMessage(shopId, chatId, message);
        } catch (Exception e) {
            log.error("Error sending message to chat {} for shop {}: {}", 
                    chatId, shopId, e.getMessage());
            return false;
        }
    }
} 