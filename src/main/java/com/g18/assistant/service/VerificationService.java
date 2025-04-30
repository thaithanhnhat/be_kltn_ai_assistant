package com.g18.assistant.service;

import com.g18.assistant.dto.request.UserRegisterRequest;

public interface VerificationService {
    
    /**
     * Generate a verification token for the given registration request
     * and store it in Redis with an expiration time
     * 
     * @param request The registration request
     * @return The generated verification token
     */
    String generateVerificationToken(UserRegisterRequest request);
    
    /**
     * Retrieve the registration request stored with the provided token
     * 
     * @param token The verification token
     * @return The stored registration request, or null if not found
     */
    UserRegisterRequest getRegistrationRequest(String token);
    
    /**
     * Delete a verification token
     * 
     * @param token The token to delete
     */
    void deleteVerificationToken(String token);
} 