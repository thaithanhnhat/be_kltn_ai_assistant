package com.g18.assistant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageGenerationResponse {
    
    private Long productId;
    
    private String originalImageUrl;
    
    private String generatedImageUrl;
    
    private String prompt;
    
    private LocalDateTime generatedAt;
    
    private String status;
    
    private String message;
} 