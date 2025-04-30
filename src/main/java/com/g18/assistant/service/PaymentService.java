package com.g18.assistant.service;

import com.g18.assistant.dto.request.CreatePaymentRequest;
import com.g18.assistant.dto.response.PaymentResponse;
import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.entity.TemporaryWallet;

import java.util.List;

public interface PaymentService {

    /**
     * Create a new payment request with a temporary wallet
     * 
     * @param request Payment request details
     * @return Payment response with wallet address
     */
    PaymentResponse createPayment(CreatePaymentRequest request);
    
    /**
     * Get payment status by ID
     * 
     * @param id Payment ID
     * @return Payment response with current status
     */
    PaymentResponse getPaymentStatus(Long id);
    
    /**
     * Manually check payment status by ID
     * This performs an explicit check of the blockchain for customer payment
     * 
     * @param id Payment ID
     * @return Payment response with updated status
     */
    PaymentResponse checkPaymentManually(Long id);
    
    /**
     * Get all payments for a user
     * 
     * @param userId User ID
     * @return List of payments
     */
    List<PaymentResponse> getUserPayments(Long userId);
    
    /**
     * Get all transactions for a payment
     * 
     * @param paymentId Payment ID
     * @return List of blockchain transactions
     */
    List<BlockchainTransaction> getPaymentTransactions(Long paymentId);
    
    /**
     * Initialize the payment service
     * Start monitoring for transactions
     */
    void initialize();
    
    /**
     * Clean up expired temporary wallets
     */
    void cleanupExpiredWallets();
} 