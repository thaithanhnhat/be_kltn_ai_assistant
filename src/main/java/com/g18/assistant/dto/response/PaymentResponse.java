package com.g18.assistant.dto.response;

import com.g18.assistant.entity.TemporaryWallet;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    
    private Long id;
    private String walletAddress;
    private BigDecimal expectedAmount;
    private BigDecimal expectedAmountVnd;
    private BigDecimal currentBnbPriceUsd;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private String paymentInstructions;
    
    public static PaymentResponse fromWallet(TemporaryWallet wallet, BigDecimal amountVnd, BigDecimal bnbPriceUsd) {
        return PaymentResponse.builder()
                .id(wallet.getId())
                .walletAddress(wallet.getWalletAddress())
                .expectedAmount(wallet.getExpectedAmount())
                .expectedAmountVnd(amountVnd)
                .currentBnbPriceUsd(bnbPriceUsd)
                .status(wallet.getStatus().name())
                .createdAt(wallet.getCreatedAt())
                .expiresAt(wallet.getExpiresAt())
                .paymentInstructions("Please send exactly " + wallet.getExpectedAmount() + 
                        " BNB to " + wallet.getWalletAddress() + 
                        " using BNB Testnet. Payment expires at " + wallet.getExpiresAt() +
                        ". You will receive " + amountVnd.toPlainString() + " VND.")
                .build();
    }
} 