package com.g18.assistant.mapper;

import com.g18.assistant.dto.CustomerDTO;
import com.g18.assistant.dto.request.CreateCustomerRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Shop;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {
    
    public CustomerDTO toDTO(Customer customer) {
        if (customer == null) {
            return null;
        }
        
        return CustomerDTO.builder()
                .id(customer.getId())
                .shopId(customer.getShop().getId())
                .fullname(customer.getFullname())
                .address(customer.getAddress())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .createdAt(customer.getCreatedAt())
                .build();
    }
    
    public Customer toEntity(CreateCustomerRequest request, Shop shop) {
        return Customer.builder()
                .shop(shop)
                .fullname(request.getFullname())
                .address(request.getAddress())
                .phone(request.getPhone())
                .email(request.getEmail())
                .build();
    }
} 