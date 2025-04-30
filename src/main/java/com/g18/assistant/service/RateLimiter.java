package com.g18.assistant.service;

/**
 * Interface for rate limiting functionality
 */
public interface RateLimiter {
    
    /**
     * Record an attempt and check if the rate limit has been exceeded
     * 
     * @param key The unique identifier (e.g., IP address or username)
     * @return true if the rate limit has been exceeded, false otherwise
     */
    boolean isLimitExceeded(String key);
    
    /**
     * Get the number of seconds remaining until the rate limit is reset
     * 
     * @param key The unique identifier (e.g., IP address or username)
     * @return The number of seconds until reset
     */
    long getRetryAfterSeconds(String key);
} 