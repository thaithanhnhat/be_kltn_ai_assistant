package com.g18.assistant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ImageGenerationRequest {
    
    @NotNull(message = "Product ID is required")
    private Long productId;
    
    @NotBlank(message = "Prompt is required")
    private String prompt;
    
    private String fileName; // Optional: If user wants to specify a name for the generated image
    
    // If we want to allow users to choose the model
    private String modelId;
} 