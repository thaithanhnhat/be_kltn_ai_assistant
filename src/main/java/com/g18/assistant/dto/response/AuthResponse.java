package com.g18.assistant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private UserResponse user;
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private int expiresIn;
} 