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
public class FacebookWebhookListDto {
    private Long shopId;
    private List<FacebookWebhookConfigDto> webhooks;
    private boolean active;
}
