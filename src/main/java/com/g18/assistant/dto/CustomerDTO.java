package com.g18.assistant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDTO {
    private Long id;
    private Long shopId;
    private String fullname;
    private String address;
    private String phone;
    private String email;
    private LocalDateTime createdAt;
} 