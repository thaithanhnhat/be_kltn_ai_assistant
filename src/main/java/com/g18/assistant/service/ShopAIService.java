package com.g18.assistant.service;

import com.g18.assistant.entity.Product;

/**
 * Service for handling AI-powered chat interactions for shop bots
 */
public interface ShopAIService {
    
    /**
     * Process a customer message through Gemini AI and return a structured response
     * 
     * @param shopId The ID of the shop
     * @param customerId The ID of the customer (or null if not registered)
     * @param customerName The name of the customer
     * @param message The message from the customer
     * @return Structured AI response with action recommendations
     */
    String processCustomerMessage(Long shopId, String customerId, String customerName, String message);
    
    /**
     * Get product recommendations based on customer query
     * 
     * @param shopId The ID of the shop
     * @param customerQuery The customer's query about products
     * @return JSON-formatted product recommendations
     */
    String getProductRecommendations(Long shopId, String customerQuery);
    
    /**
     * Process order-related requests through AI
     * 
     * @param shopId The ID of the shop
     * @param customerId The ID of the customer
     * @param orderRequest The order request details
     * @return JSON-formatted order processing response
     */
    String processOrderRequest(Long shopId, String customerId, String orderRequest);
    
    /**
     * Get customer info required for completing an order
     * 
     * @param customerInput The customer's input containing partial delivery information
     * @return JSON with required missing fields and validation results
     */
    String validateDeliveryInfo(String customerInput);
    
    /**
     * Get product by ID for a specific shop
     * 
     * @param shopId The ID of the shop
     * @param productId The ID of the product
     * @return The product if found, null otherwise
     */
    Product getProductById(Long shopId, Long productId);
} 