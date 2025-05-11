package com.g18.assistant.service;

import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;

import java.util.List;

public interface TelegramBotService {
    
    /**
     * Start a Telegram bot for a shop using the provided token
     * 
     * @param shopId The ID of the shop
     * @param username The username of the shop owner
     * @return true if the bot was started successfully
     */
    boolean startBot(Long shopId, String username);
    
    /**
     * Stop a Telegram bot for a shop
     * 
     * @param shopId The ID of the shop
     * @param username The username of the shop owner
     * @return true if the bot was stopped successfully
     */
    boolean stopBot(Long shopId, String username);
    
    /**
     * Get the status of a Telegram bot for a shop
     * 
     * @param shopId The ID of the shop
     * @param username The username of the shop owner
     * @return true if the bot is running, false otherwise
     */
    boolean getBotStatus(Long shopId, String username);
    
    /**
     * Get recent messages received by a shop's Telegram bot
     * 
     * @param shopId The ID of the shop
     * @param username The username of the shop owner
     * @param limit The maximum number of messages to return (default 50)
     * @return List of telegram messages
     */
    List<TelegramMessage> getRecentMessages(Long shopId, String username, int limit);
    
    /**
     * Send a message to a Telegram chat
     * 
     * @param shopId The ID of the shop
     * @param username The username of the shop owner
     * @param chatId The ID of the chat
     * @param message The message to send
     * @return true if the message was sent successfully
     */
    boolean sendMessage(Long shopId, String username, Long chatId, String message);
} 