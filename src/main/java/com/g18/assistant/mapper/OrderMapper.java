package com.g18.assistant.mapper;

import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Order;
import com.g18.assistant.entity.Order.OrderStatus;
import com.g18.assistant.entity.Product;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {
    
    public OrderDTO toDTO(Order order) {
        if (order == null) {
            return null;
        }
        
        return OrderDTO.builder()
                .id(order.getId())
                .customerId(order.getCustomer().getId())
                .customerName(order.getCustomer().getFullname())
                .productId(order.getProduct().getId())
                .productName(order.getProduct().getName())
                .note(order.getNote())
                .quantity(order.getQuantity())
                .deliveryUnit(order.getDeliveryUnit())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .build();
    }
    
    public Order toEntity(CreateOrderRequest request, Customer customer, Product product) {
        return Order.builder()
                .customer(customer)
                .product(product)
                .note(request.getNote())
                .quantity(request.getQuantity())
                .deliveryUnit(request.getDeliveryUnit())
                .status(OrderStatus.PENDING)
                .build();
    }
} 