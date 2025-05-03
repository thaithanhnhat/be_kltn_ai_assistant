package com.g18.assistant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramMessageResponse {
    
    private Long id;
    private Long shopId;
    private String userId;
    private String username;
    private String messageText;
    private Long chatId;
    private String fileUrl;
    private String fileType;
    private LocalDateTime receivedAt;
    private Boolean processed;
} 