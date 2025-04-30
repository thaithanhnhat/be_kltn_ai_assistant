package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "blockchain_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockchainTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tx_hash", nullable = false, unique = true)
    private String txHash;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "gas_used")
    private BigDecimal gasUsed;

    @Column(name = "gas_price")
    private BigDecimal gasPrice;

    @Column(name = "block_number")
    private Long blockNumber;

    @Column(name = "block_timestamp")
    private LocalDateTime blockTimestamp;

    @Column(name = "transaction_timestamp", nullable = false)
    private LocalDateTime transactionTimestamp;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Column(name = "balance_updated", nullable = false)
    private boolean balanceUpdated = false;

    @ManyToOne
    @JoinColumn(name = "wallet_id")
    private TemporaryWallet wallet;

    public enum TransactionStatus {
        PENDING, CONFIRMED, FAILED
    }

    public enum TransactionType {
        DEPOSIT, SWEEP
    }

    @PrePersist
    protected void onCreate() {
        transactionTimestamp = LocalDateTime.now();
        if (status == null) {
            status = TransactionStatus.PENDING;
        }
    }
} 