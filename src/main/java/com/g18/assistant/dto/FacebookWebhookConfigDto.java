package com.g18.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacebookWebhookConfigDto {
    private String webhookUrl;
    private String verifyToken;
    private String pageId;
    private String pageName;
    private String accessToken;
    private List<String> subscribedEvents;
    private boolean active;
    
    // For creating new configuration
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        private String pageId;
        private String pageName;
        private String accessToken;
        private String verifyToken;
        private String webhookUrl;
        private List<String> subscribedEvents;
    }
    
    // For updating existing configuration
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String pageName;
        private String accessToken;
        private String verifyToken;
        private String webhookUrl;
        private List<String> subscribedEvents;
        private Boolean active;
    }
}