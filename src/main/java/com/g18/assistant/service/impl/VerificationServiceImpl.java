package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

    @Qualifier("redisTemplateUserRequest")
    private final RedisTemplate<String, UserRegisterRequest> redisTemplate;
    
    private static final String TOKEN_PREFIX = "verification:";
    
    @Value("${app.verification.token.expiration:86400}") // Default: 24 hours in seconds
    private long tokenExpirationSeconds;
    
    @Override
    public String generateVerificationToken(UserRegisterRequest request) {
        String token = UUID.randomUUID().toString();
        String key = TOKEN_PREFIX + token;
        
        redisTemplate.opsForValue().set(key, request, tokenExpirationSeconds, TimeUnit.SECONDS);
        
        return token;
    }

    @Override
    public UserRegisterRequest getRegistrationRequest(String token) {
        String key = TOKEN_PREFIX + token;
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void deleteVerificationToken(String token) {
        String key = TOKEN_PREFIX + token;
        redisTemplate.delete(key);
    }
} 