package com.g18.assistant.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VNPayCreateRequest {
    
    @NotNull(message = "Amount is required")
    @Min(value = 10000, message = "Amount must be at least 10,000 VND")
    private BigDecimal amount;
    
    private String bankCode; // Optional bank code
    
    private String language; // Optional language (vn/en)
    
    @Builder.Default
    private String description = "Nạp tiền vào tài khoản";
} 