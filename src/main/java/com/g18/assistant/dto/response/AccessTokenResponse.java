package com.g18.assistant.dto.response;

import com.g18.assistant.entity.AccessToken;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenResponse {
    
    private Long id;
    private Long userId;
    private Long shopId;
    private String accessToken;
    private AccessToken.TokenStatus status;
    private AccessToken.TokenMethod method;
} 