package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "temporary_wallets")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, unique = true)
    private String walletAddress;

    @Column(name = "private_key", nullable = false)
    private String privateKey;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "transaction_id")
    private Long transactionId;
    
    @Column(name = "expected_amount")
    private BigDecimal expectedAmount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private WalletStatus status;

    @Column(name = "swept", nullable = false)
    private boolean swept;

    public enum WalletStatus {
        PENDING, PAID, EXPIRED, SWEPT
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24); // Default 24 hour expiry
        }
        if (status == null) {
            status = WalletStatus.PENDING;
        }
        swept = false;
    }
} 