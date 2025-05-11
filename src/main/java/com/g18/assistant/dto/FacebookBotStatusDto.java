package com.g18.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacebookBotStatusDto {
    private Long shopId;
    private boolean active;
    private String webhookUrl;
} 