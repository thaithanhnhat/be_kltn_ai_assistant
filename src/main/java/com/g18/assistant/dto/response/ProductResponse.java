package com.g18.assistant.dto.response;

import com.g18.assistant.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponse {
    
    private Long id;
    private Long shopId;
    private String shopName;
    private String name;
    private BigDecimal price;
    private String description;
    private String category;
    private Integer stock;
    private String imageBase64;
    private Map<String, String> customFields;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    public static ProductResponse fromEntity(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .shopId(product.getShop().getId())
                .shopName(product.getShop().getName())
                .name(product.getName())
                .price(product.getPrice())
                .description(product.getDescription())
                .category(product.getCategory())
                .stock(product.getStock())
                .imageBase64(product.getImageBase64())
                .customFields(product.getCustomFields() != null 
                    ? new HashMap<>(product.getCustomFields()) 
                    : new HashMap<>())
                .active(product.getActive())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
    
    public static ProductResponse fromEntityWithoutImage(Product product) {
        ProductResponse response = fromEntity(product);
        response.setImageBase64(null); // Don't send image in list views
        return response;
    }
} 