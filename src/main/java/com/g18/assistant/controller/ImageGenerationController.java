package com.g18.assistant.controller;

import com.g18.assistant.dto.request.ImageGenerationRequest;
import com.g18.assistant.dto.response.ImageGenerationResponse;
import com.g18.assistant.service.GeminiAiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/products/image-generation")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class ImageGenerationController {

    private final GeminiAiService geminiAiService;

    /**
     * Generate a new product image using Gemini AI based on the original product image and a text prompt
     * 
     * @param request The image generation request containing productId and prompt
     * @return The generated image response with URLs and status information
     */
    @PostMapping
    public ResponseEntity<ImageGenerationResponse> generateProductImage(
            @Valid @RequestBody ImageGenerationRequest request) {
        
        try {
            log.info("Received image generation request for product ID: {} with prompt: {}", 
                    request.getProductId(), request.getPrompt());
            
            ImageGenerationResponse response = geminiAiService.generateProductImage(request);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Error generating product image: {}", e.getMessage());
            
            ImageGenerationResponse errorResponse = ImageGenerationResponse.builder()
                    .productId(request.getProductId())
                    .prompt(request.getPrompt())
                    .status("ERROR")
                    .message("Error generating image: " + e.getMessage())
                    .build();
            
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }
} 