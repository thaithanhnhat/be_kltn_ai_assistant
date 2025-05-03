package com.g18.assistant.service;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.entity.Shop;

import java.util.List;

public interface ShopService {
    
    /**
     * Create a new shop
     * 
     * @param username The username (email) of the user
     * @param request The shop creation request
     * @return The created shop
     */
    ShopResponse createShop(String username, ShopRequest request);
    
    /**
     * Get all shops for a user
     * 
     * @param username The username (email) of the user
     * @return List of shops
     */
    List<ShopResponse> getUserShops(String username);
    
    /**
     * Get active shops for a user
     * 
     * @param username The username (email) of the user
     * @return List of active shops
     */
    List<ShopResponse> getActiveShops(String username);
    
    /**
     * Get a specific shop by ID
     * 
     * @param shopId The ID of the shop
     * @param username The username (email) of the requesting user
     * @return The shop if it belongs to the user
     * @throws SecurityException if the shop doesn't belong to the user
     */
    ShopResponse getShopById(Long shopId, String username);
    
    /**
     * Update shop info
     * 
     * @param shopId The ID of the shop to update
     * @param username The username (email) of the requesting user
     * @param request The shop update request
     * @return The updated shop
     * @throws SecurityException if the shop doesn't belong to the user
     */
    ShopResponse updateShop(Long shopId, String username, ShopRequest request);
    
    /**
     * Update shop status
     * 
     * @param shopId The ID of the shop to update
     * @param username The username (email) of the requesting user
     * @param status The new status
     * @return The updated shop
     * @throws SecurityException if the shop doesn't belong to the user
     */
    ShopResponse updateShopStatus(Long shopId, String username, Shop.ShopStatus status);
    
    /**
     * Delete a shop
     * 
     * @param shopId The ID of the shop to delete
     * @param username The username (email) of the requesting user
     * @throws SecurityException if the shop doesn't belong to the user
     */
    void deleteShop(Long shopId, String username);
    
    /**
     * Check if a shop exists and belongs to the user
     * 
     * @param shopId The ID of the shop
     * @param username The username (email) of the requesting user
     * @return The shop entity if found
     * @throws SecurityException if the shop doesn't belong to the user
     */
    Shop validateUserShop(Long shopId, String username);
    
    /**
     * Check if a user is the owner of a shop
     * 
     * @param userId The ID of the user
     * @param shopId The ID of the shop
     * @return true if the user is the owner, false otherwise
     */
    boolean isShopOwner(Long userId, Long shopId);
} 