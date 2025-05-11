package com.g18.assistant.repository;

import com.g18.assistant.entity.VNPayTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VNPayTransactionRepository extends JpaRepository<VNPayTransaction, Long> {
    
    Optional<VNPayTransaction> findByOrderId(String orderId);
    
    Optional<VNPayTransaction> findByTransactionId(String transactionId);
    
    List<VNPayTransaction> findByUserId(Long userId);
    
    List<VNPayTransaction> findByUserIdAndStatus(Long userId, VNPayTransaction.TransactionStatus status);
} 