package com.g18.assistant.service;

import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;

public interface FacebookBotService {
    FacebookWebhookConfigDto configureWebhook(Long shopId);
    boolean verifyWebhook(String verifyToken, String challenge);
    void saveAccessToken(Long shopId, String accessToken, String pageId);
    void startBot(Long shopId);
    void stopBot(Long shopId);
    FacebookBotStatusDto getBotStatus(Long shopId);
    void handleIncomingMessage(String requestBody);
    void sendMessage(Long shopId, String recipientId, String message);
} 