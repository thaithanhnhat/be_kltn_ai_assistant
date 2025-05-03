package com.g18.assistant.dto.response;

import com.g18.assistant.entity.VNPayTransaction;
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
public class VNPayResponse {
    
    private Long id;
    private String orderId;
    private String transactionId;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String paymentUrl;
    private String responseCode;
    private String bankCode;
    private String cardType;
    private String payDate;
    
    public static VNPayResponse fromEntity(VNPayTransaction transaction) {
        return VNPayResponse.builder()
                .id(transaction.getId())
                .orderId(transaction.getOrderId())
                .transactionId(transaction.getTransactionId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus().name())
                .createdAt(transaction.getCreatedAt())
                .updatedAt(transaction.getUpdatedAt())
                .responseCode(transaction.getResponseCode())
                .bankCode(transaction.getBankCode())
                .cardType(transaction.getCardType())
                .payDate(transaction.getPayDate())
                .build();
    }
    
    public static VNPayResponse fromEntityWithPaymentUrl(VNPayTransaction transaction, String paymentUrl) {
        VNPayResponse response = fromEntity(transaction);
        response.setPaymentUrl(paymentUrl);
        return response;
    }
} 