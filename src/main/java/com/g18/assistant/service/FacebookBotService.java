package com.g18.assistant.service;

import com.g18.assistant.dto.FacebookBotStatusDto;
import com.g18.assistant.dto.FacebookWebhookConfigDto;

import java.util.List;

public interface FacebookBotService {
    FacebookWebhookConfigDto configureWebhook(Long shopId);
    boolean verifyWebhook(String verifyToken, String challenge);
    void saveAccessToken(Long shopId, String accessToken, String pageId);
    void startBot(Long shopId);
    void stopBot(Long shopId);
    FacebookBotStatusDto getBotStatus(Long shopId);
    void handleIncomingMessage(String requestBody);
    void sendMessage(Long shopId, String recipientId, String message);
    
    // New methods for multiple fanpage support
    void savePageConfiguration(Long shopId, FacebookWebhookConfigDto.CreateRequest request);
    void updatePageConfiguration(Long shopId, String pageId, FacebookWebhookConfigDto.UpdateRequest request);
    void deletePageConfiguration(Long shopId, String pageId);
    List<FacebookWebhookConfigDto> getShopPageConfigurations(Long shopId);
    FacebookWebhookConfigDto getPageConfiguration(Long shopId, String pageId);
    void subscribePageToWebhook(Long shopId, String pageId);
    void unsubscribePageFromWebhook(Long shopId, String pageId);
}