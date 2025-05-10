package com.g18.assistant.mapper;

import com.g18.assistant.dto.FeedbackDTO;
import com.g18.assistant.dto.request.CreateFeedbackRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Feedback;
import com.g18.assistant.entity.Product;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class FeedbackMapper {
    
    public FeedbackDTO toDTO(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        
        return FeedbackDTO.builder()
                .id(feedback.getId())
                .customerId(feedback.getCustomer().getId())
                .customerName(feedback.getCustomer().getFullname())
                .productId(feedback.getProduct().getId())
                .productName(feedback.getProduct().getName())
                .content(feedback.getContent())
                .time(feedback.getTime())
                .build();
    }
    
    public Feedback toEntity(CreateFeedbackRequest request, Customer customer, Product product) {
        return Feedback.builder()
                .customer(customer)
                .product(product)
                .content(request.getContent())
                .time(LocalDateTime.now())
                .build();
    }
} 