package com.g18.assistant.service.impl;

import com.g18.assistant.service.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Implementation of RateLimiter using Redis to track login attempts
 */
@RequiredArgsConstructor
public class RedisRateLimiter implements RateLimiter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    private final int maxAttempts;
    private final long windowSeconds;
    
    @Override
    public boolean isLimitExceeded(String key) {
        String fullKey = keyPrefix + key;
        
        // Check if key exists
        Boolean exists = redisTemplate.hasKey(fullKey);
        
        if (exists == null || !exists) {
            // First attempt, initialize the counter
            redisTemplate.opsForValue().set(fullKey, "1", windowSeconds, TimeUnit.SECONDS);
            return false;
        }
        
        // Increment attempt counter
        Long attempts = redisTemplate.opsForValue().increment(fullKey);
        
        // If this is the first increment, set expiry
        if (attempts != null && attempts == 1) {
            redisTemplate.expire(fullKey, windowSeconds, TimeUnit.SECONDS);
        }
        
        // Check if limit exceeded
        return attempts != null && attempts > maxAttempts;
    }
    
    @Override
    public long getRetryAfterSeconds(String key) {
        String fullKey = keyPrefix + key;
        
        // Get time to live
        Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
        
        return ttl != null && ttl > 0 ? ttl : windowSeconds;
    }
} 