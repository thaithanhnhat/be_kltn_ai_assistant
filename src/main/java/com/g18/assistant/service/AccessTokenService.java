package com.g18.assistant.service;

import com.g18.assistant.dto.request.AccessTokenRequest;
import com.g18.assistant.dto.response.AccessTokenResponse;
import com.g18.assistant.entity.AccessToken;

import java.util.List;

public interface AccessTokenService {
    
    /**
     * Add a new access token for the authenticated user
     * 
     * @param username The username (email) of the user
     * @param request The access token request containing shopId
     * @return The created access token
     */
    AccessTokenResponse addAccessToken(String username, AccessTokenRequest request);
    
    /**
     * Get all access tokens for the authenticated user
     * 
     * @param username The username (email) of the user
     * @return List of access tokens
     */
    List<AccessTokenResponse> getAllTokens(String username);
    
    /**
     * Get all access tokens for a specific shop
     * 
     * @param username The username (email) of the user
     * @param shopId The ID of the shop
     * @return List of access tokens for the shop
     */
    List<AccessTokenResponse> getShopTokens(String username, Long shopId);
    
    /**
     * Get active access tokens for a specific method
     * 
     * @param username The username (email) of the user
     * @param method The token method (TELEGRAM, FACEBOOK)
     * @return List of active access tokens for the specified method
     */
    List<AccessTokenResponse> getActiveTokensByMethod(String username, AccessToken.TokenMethod method);
    
    /**
     * Get active access tokens for a specific shop and method
     * 
     * @param username The username (email) of the user
     * @param shopId The ID of the shop
     * @param method The token method (TELEGRAM, FACEBOOK)
     * @return List of active access tokens for the specified shop and method
     */
    List<AccessTokenResponse> getActiveShopTokensByMethod(String username, Long shopId, AccessToken.TokenMethod method);
    
    /**
     * Get a specific token by ID
     * 
     * @param tokenId The ID of the token
     * @param username The username (email) of the requesting user
     * @return The access token if it belongs to the user
     * @throws SecurityException if the token doesn't belong to the user
     */
    AccessTokenResponse getTokenById(Long tokenId, String username);
    
    /**
     * Update the status of an access token
     * 
     * @param tokenId The ID of the token to update
     * @param status The new status
     * @param username The username (email) of the requesting user
     * @return The updated access token
     * @throws SecurityException if the token doesn't belong to the user
     */
    AccessTokenResponse updateTokenStatus(Long tokenId, AccessToken.TokenStatus status, String username);
    
    /**
     * Delete an access token
     * 
     * @param tokenId The ID of the token to delete
     * @param username The username (email) of the requesting user
     * @throws SecurityException if the token doesn't belong to the user
     */
    void deleteToken(Long tokenId, String username);
} 