package com.g18.assistant.dto.request;

import com.g18.assistant.entity.AccessToken;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessTokenRequest {
    
    @NotBlank(message = "Access token is required")
    private String accessToken;
    
    @NotNull(message = "Method is required")
    private AccessToken.TokenMethod method;
    
    @NotNull(message = "Shop ID is required")
    private Long shopId;
} 