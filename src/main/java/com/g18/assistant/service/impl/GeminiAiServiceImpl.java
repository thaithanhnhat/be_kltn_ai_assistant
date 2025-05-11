package com.g18.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.g18.assistant.dto.request.ImageGenerationRequest;
import com.g18.assistant.dto.request.ProductRequest;
import com.g18.assistant.dto.response.ImageGenerationResponse;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.service.GeminiAiService;
import com.g18.assistant.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiAiServiceImpl implements GeminiAiService {

    private final ProductService productService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    @Value("${app.gemini.model-id}")
    private String defaultModelId;

    @Value("${app.gemini.api-url}")
    private String geminiApiBaseUrl;

    @Override
    public ImageGenerationResponse generateProductImage(ImageGenerationRequest request) throws IOException {
        try {
            // Get the product to modify its image
            ProductResponse product = productService.getProductById(request.getProductId());
            if (product == null) {
                throw new EntityNotFoundException("Product not found with id: " + request.getProductId());
            }
            
            log.info("Processing image generation request for product ID: {}, prompt: {}", 
                    product.getId(), request.getPrompt());
            
            // Check if product has image
            if (product.getImageBase64() == null || product.getImageBase64().isEmpty()) {
                throw new IllegalStateException("Product has no image to modify");
            }
            
            // Save the base64 image to a temporary file
            byte[] imageData = Base64.getDecoder().decode(product.getImageBase64());
            File tempImageFile = File.createTempFile("product_image_", ".png");
            try (FileOutputStream fos = new FileOutputStream(tempImageFile)) {
                fos.write(imageData);
            }
            log.info("Saved product image to temporary file: {}", tempImageFile.getAbsolutePath());
            
            // 1. Upload the file to Gemini API
            String fileUri = uploadFileToGemini(tempImageFile);
            log.info("Successfully uploaded image to Gemini, file URI: {}", fileUri);
            
            // 2. Create the image generation request
            // Using the stream generation API for better compatibility with the Python example
            String modelId = request.getModelId() != null ? request.getModelId() : defaultModelId;
            String apiEndpoint = String.format("%s/%s:streamGenerateContent?key=%s", 
                    geminiApiBaseUrl, modelId, geminiApiKey);
            
            // 3. Create the request body in the format the API expects
            ObjectNode requestBody = createRequestBodyForStreamGeneration(fileUri, request.getPrompt());
            
            // 4. Make the API request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            log.info("Sending request to Gemini API at: {}", apiEndpoint);
            
            ResponseEntity<String> response = restTemplate.exchange(
                apiEndpoint,
                HttpMethod.POST,
                entity,
                String.class
            );
            
            log.debug("Received response from Gemini API: {}", response.getBody());
            
            // 5. Process the response and extract the image
            byte[] generatedImageBytes = processStreamingResponse(response.getBody());
            
            if (generatedImageBytes == null) {
                // Fallback to regular extraction if streaming processing failed
                generatedImageBytes = extractImageFromResponse(response.getBody());
            }
            
            if (generatedImageBytes == null) {
                log.error("No image data found in the Gemini API response");
                return ImageGenerationResponse.builder()
                        .productId(request.getProductId())
                        .originalImageUrl("/api/products/" + product.getId() + "/image")
                        .prompt(request.getPrompt())
                        .generatedAt(LocalDateTime.now())
                        .status("ERROR")
                        .message("No image generated by the AI model")
                        .build();
            }
            
            // 6. Convert the image bytes to base64 for product storage
            String generatedImageBase64 = Base64.getEncoder().encodeToString(generatedImageBytes);
            
            // 7. Update the product with the new image
            try {
                ProductRequest productUpdateRequest = ProductRequest.builder()
                        .name(product.getName())
                        .price(product.getPrice())
                        .description(product.getDescription())
                        .category(product.getCategory())
                        .stock(product.getStock())
                        .imageBase64(generatedImageBase64)
                        .customFields(product.getCustomFields())
                        .build();
                
                productService.updateProduct(product.getShopId(), product.getId(), productUpdateRequest);
                log.info("Successfully updated product with the generated image");
            } catch (Exception e) {
                log.error("Failed to update product with generated image: {}", e.getMessage(), e);
                return ImageGenerationResponse.builder()
                        .productId(request.getProductId())
                        .originalImageUrl("/api/products/" + product.getId() + "/image")
                        .generatedImageUrl(null)
                        .prompt(request.getPrompt())
                        .generatedAt(LocalDateTime.now())
                        .status("ERROR")
                        .message("Image was generated but failed to update product: " + e.getMessage())
                        .build();
            }
            
            // 8. Return successful response
            return ImageGenerationResponse.builder()
                    .productId(request.getProductId())
                    .originalImageUrl("/api/products/" + product.getId() + "/image")
                    .generatedImageUrl("/api/products/" + product.getId() + "/image?t=" + System.currentTimeMillis())
                    .prompt(request.getPrompt())
                    .generatedAt(LocalDateTime.now())
                    .status("SUCCESS")
                    .message("Image generated successfully")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error generating image with Gemini API: {}", e.getMessage(), e);
            return ImageGenerationResponse.builder()
                    .productId(request.getProductId())
                    .originalImageUrl(null)
                    .prompt(request.getPrompt())
                    .generatedAt(LocalDateTime.now())
                    .status("ERROR")
                    .message("Error generating image: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public String uploadImageToGemini(byte[] imageFile, String displayName) throws IOException {
        File tempFile = File.createTempFile("upload_", ".png");
        try {
            FileUtils.writeByteArrayToFile(tempFile, imageFile);
            return uploadFileToGemini(tempFile);
        } finally {
            tempFile.delete();
        }
    }
    
    /**
     * Uploads a file to the Gemini API similar to the Python client.files.upload method
     */
    private String uploadFileToGemini(File file) throws IOException {
        // The URL for file uploads
        String uploadUrl = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=" + geminiApiKey;
        
        // First, initiate the upload
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Goog-Upload-Protocol", "resumable");
        headers.add("X-Goog-Upload-Command", "start");
        headers.add("X-Goog-Upload-Header-Content-Length", String.valueOf(file.length()));
        headers.add("X-Goog-Upload-Header-Content-Type", "image/png");
        
        // Create the request body with the file's display name
        ObjectNode requestBody = JsonNodeFactory.instance.objectNode();
        ObjectNode fileObj = requestBody.putObject("file");
        fileObj.put("display_name", file.getName());
        
        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        
        log.info("Initiating file upload to Gemini API: {}", uploadUrl);
        ResponseEntity<String> response = restTemplate.exchange(
                uploadUrl,
                HttpMethod.POST,
                entity,
                String.class
        );
        
        // Get the upload URL from the response header
        String resumableUploadUrl = response.getHeaders().getFirst("X-Goog-Upload-URL");
        if (resumableUploadUrl == null) {
            throw new IOException("Failed to get upload URL from Gemini API");
        }
        
        // Now upload the file content
        byte[] fileContent = Files.readAllBytes(file.toPath());
        
        HttpHeaders uploadHeaders = new HttpHeaders();
        uploadHeaders.add("X-Goog-Upload-Command", "upload, finalize");
        uploadHeaders.add("X-Goog-Upload-Offset", "0");
        uploadHeaders.setContentLength(fileContent.length);
        
        HttpEntity<byte[]> uploadEntity = new HttpEntity<>(fileContent, uploadHeaders);
        
        log.info("Uploading file content to Gemini API");
        ResponseEntity<String> uploadResponse = restTemplate.exchange(
                resumableUploadUrl,
                HttpMethod.POST,
                uploadEntity,
                String.class
        );
        
        // Parse the response to get the file URI
        JsonNode responseJson = objectMapper.readTree(uploadResponse.getBody());
        String fileUri = responseJson.path("file").path("uri").asText();
        
        if (fileUri == null || fileUri.isEmpty()) {
            throw new IOException("Failed to get file URI from upload response");
        }
        
        return fileUri;
    }
    
    /**
     * Creates a request body following the format used in the Python example
     */
    private ObjectNode createRequestBodyForStreamGeneration(String fileUri, String prompt) {
        ObjectNode requestBody = JsonNodeFactory.instance.objectNode();
        ArrayNode contents = requestBody.putArray("contents");
        
        // User content with file and prompt
        ObjectNode userContent = contents.addObject();
        userContent.put("role", "user");
        ArrayNode userParts = userContent.putArray("parts");
        
        // File part
        ObjectNode filePart = userParts.addObject();
        ObjectNode fileData = filePart.putObject("fileData");
        fileData.put("mimeType", "image/png");
        fileData.put("fileUri", fileUri);
        
        // Text prompt part
        ObjectNode textPart = userParts.addObject();
        textPart.put("text", prompt);
        
        // Generation config
        ObjectNode generationConfig = requestBody.putObject("generationConfig");
        ArrayNode responseModalities = generationConfig.putArray("responseModalities");
        responseModalities.add("image");
        responseModalities.add("text");
        
        return requestBody;
    }
    
    /**
     * Extracts the generated image from the Gemini API response
     */
    private byte[] extractImageFromResponse(String responseBody) {
        try {
            // Parse the response JSON
            JsonNode rootNode = objectMapper.readTree(responseBody);
            
            // Extract from candidates part (most likely location based on Python example)
            JsonNode candidates = rootNode.path("candidates");
            if (!candidates.isEmpty()) {
                for (JsonNode candidate : candidates) {
                    JsonNode content = candidate.path("content");
                    if (!content.isMissingNode()) {
                        JsonNode parts = content.path("parts");
                        if (!parts.isEmpty()) {
                            for (JsonNode part : parts) {
                                // Check for inline data
                                if (part.has("inlineData")) {
                                    String base64Data = part.path("inlineData").path("data").asText();
                                    log.info("Found base64 image data in response");
                                    return Base64.getDecoder().decode(base64Data);
                                }
                            }
                        }
                    }
                }
            }
            
            // Also check the non-streaming response format
            JsonNode contents = rootNode.path("contents");
            if (!contents.isEmpty()) {
                for (JsonNode content : contents) {
                    if (content.has("parts")) {
                        JsonNode parts = content.path("parts");
                        for (JsonNode part : parts) {
                            if (part.has("inlineData")) {
                                String base64Data = part.path("inlineData").path("data").asText();
                                log.info("Found base64 image data in contents");
                                return Base64.getDecoder().decode(base64Data);
                            }
                        }
                    }
                }
            }
            
            // Fallback: Check for text that might contain base64 data
            String responseText = rootNode.toString();
            int base64Start = responseText.indexOf("\"data\":\"");
            if (base64Start != -1) {
                base64Start += 8; // Length of "data":"
                int base64End = responseText.indexOf("\"", base64Start);
                if (base64End != -1) {
                    String base64Data = responseText.substring(base64Start, base64End);
                    log.info("Found potential base64 data by manual search");
                    return Base64.getDecoder().decode(base64Data);
                }
            }
            
            log.error("No image data found in Gemini API response");
            return null;
        } catch (Exception e) {
            log.error("Error extracting image data from response: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Attempts to extract image data from the streaming response, which may not be complete in a single chunk
     */
    private byte[] processStreamingResponse(String responseBody) {
        try {
            log.debug("Processing streaming response: {}", responseBody);
            
            // For streaming responses, we might need to handle multiple chunks
            // The response might look different from the simple JSON format
            
            // Check if we have completed chunks that contain base64 data
            if (responseBody.contains("\"inlineData\"")) {
                int startIndex = responseBody.indexOf("\"data\":\"");
                if (startIndex >= 0) {
                    startIndex += 8; // Length of "data":"
                    int endIndex = responseBody.indexOf("\"", startIndex);
                    if (endIndex > startIndex) {
                        String base64Data = responseBody.substring(startIndex, endIndex);
                        log.info("Found base64 data in streaming response, length: {}", base64Data.length());
                        return Base64.getDecoder().decode(base64Data);
                    }
                }
            }
            
            // Try to use the regular JSON parsing approach as fallback
            return extractImageFromResponse(responseBody);
            
        } catch (Exception e) {
            log.error("Failed to process streaming response: {}", e.getMessage(), e);
            return null;
        }
    }
} 