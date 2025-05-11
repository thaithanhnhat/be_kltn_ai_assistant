package com.g18.assistant.dto.response;

import com.g18.assistant.entity.Shop;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopResponse {
    
    private Long id;
    private Long userId;
    private String name;
    private Shop.ShopStatus status;
} 