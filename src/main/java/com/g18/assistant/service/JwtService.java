package com.g18.assistant.service;

import com.g18.assistant.entity.User;

import java.util.Map;

public interface JwtService {
    
    /**
     * Generate an access token for a user
     * 
     * @param user The user for whom to generate the token
     * @return The generated access token
     */
    String generateAccessToken(User user);
    
    /**
     * Generate a refresh token for a user
     * 
     * @param user The user for whom to generate the token
     * @return The generated refresh token
     */
    String generateRefreshToken(User user);
    
    /**
     * Validate a token and extract the username (subject)
     * 
     * @param token The token to validate
     * @return The username extracted from the token
     */
    String validateTokenAndGetUsername(String token);
    
    /**
     * Check if a token is valid
     * 
     * @param token The token to check
     * @return true if the token is valid, false otherwise
     */
    boolean isTokenValid(String token);
    
    /**
     * Extract all claims from a token
     * 
     * @param token The token from which to extract claims
     * @return A map of claims
     */
    Map<String, Object> extractAllClaims(String token);
} 