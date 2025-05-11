package com.g18.assistant.service;

import com.g18.assistant.dto.request.VNPayCreateRequest;
import com.g18.assistant.dto.response.VNPayResponse;
import com.g18.assistant.entity.VNPayTransaction;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface VNPayService {
    
    /**
     * Create a payment URL for VNPay
     * 
     * @param userId User ID making the payment
     * @param request Payment request details
     * @return Response containing payment URL and transaction info
     */
    VNPayResponse createPayment(Long userId, VNPayCreateRequest request);
    
    /**
     * Process VNPay payment response (IPN)
     * 
     * @param params Request parameters from VNPay
     * @return Updated transaction
     */
    VNPayTransaction processPaymentReturn(Map<String, String> params);
    
    /**
     * Get a specific transaction by ID
     * 
     * @param id Transaction ID
     * @return Transaction info
     */
    VNPayResponse getTransaction(Long id);
    
    /**
     * Get a transaction by order ID
     * 
     * @param orderId Order ID
     * @return Transaction info
     */
    VNPayResponse getTransactionByOrderId(String orderId);
    
    /**
     * Get all transactions for a user
     * 
     * @param userId User ID
     * @return List of transactions
     */
    List<VNPayResponse> getUserTransactions(Long userId);
} 