package com.g18.assistant.dto.response;

import com.g18.assistant.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Specialized DTO for sending products to AI for consultation,
 * with minimal fields to reduce payload size
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductConsultationResponse {
    private Long id;
    private String name;
    private BigDecimal price;
    private String category;
    private String description;
    private Integer stock;
    private Map<String, String> customFields;
    
    public static ProductConsultationResponse fromEntity(Product product) {
        return ProductConsultationResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .category(product.getCategory())
                .description(product.getDescription())
                .stock(product.getStock())
                .customFields(product.getCustomFields())
                .build();
    }
    
    public static List<ProductConsultationResponse> fromEntities(List<Product> products) {
        return products.stream()
                .map(ProductConsultationResponse::fromEntity)
                .collect(Collectors.toList());
    }
} 