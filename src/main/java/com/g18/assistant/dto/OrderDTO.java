package com.g18.assistant.dto;

import com.g18.assistant.entity.Order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private Long customerId;
    private String customerName;
    private Long productId;
    private String productName;
    private String note;
    private Integer quantity;
    private String deliveryUnit;
    private OrderStatus status;
    private LocalDateTime createdAt;
} 