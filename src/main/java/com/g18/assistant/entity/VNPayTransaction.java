package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vnpay_transactions", 
       uniqueConstraints = {
           @UniqueConstraint(name = "UK_order_id", columnNames = {"orderId"})
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VNPayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = true)
    private String transactionId; // VNPay transaction ID
    
    @Column(nullable = false, unique = true)
    private String orderId; // Our order ID
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private BigDecimal amount;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    private String responseCode;
    
    private String bankCode;
    
    private String cardType;
    
    private String payDate;
    
    private String secureHash;
    
    @Column(nullable = false)
    private boolean balanceUpdated;
    
    public enum TransactionStatus {
        PENDING, COMPLETED, FAILED, CANCELLED
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
        balanceUpdated = false;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 