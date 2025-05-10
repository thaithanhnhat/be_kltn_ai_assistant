package com.g18.assistant.service;

import com.g18.assistant.dto.request.ImageGenerationRequest;
import com.g18.assistant.dto.response.ImageGenerationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface GeminiAiService {
    
    /**
     * Generate a modified product image based on user prompt using Gemini AI
     * 
     * @param request The image generation request containing productId and prompt
     * @return ImageGenerationResponse containing the URL of the generated image
     * @throws IOException if there's an error in file processing or API communication
     */
    ImageGenerationResponse generateProductImage(ImageGenerationRequest request) throws IOException;
    
    /**
     * Upload an image to Gemini AI
     * 
     * @param imageFile The image file to upload
     * @param displayName Display name for the uploaded image
     * @return The file URI for the uploaded image
     * @throws IOException if there's an error uploading the image
     */
    String uploadImageToGemini(byte[] imageFile, String displayName) throws IOException;
} 