package com.g18.assistant.controller;

import com.g18.assistant.dto.request.ProductRequest;
import com.g18.assistant.dto.response.PageResponse;
import com.g18.assistant.dto.response.ProductConsultationResponse;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.entity.Product;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.ProductService;
import com.g18.assistant.service.ShopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final ShopService shopService;
    private final ProductRepository productRepository;

    // Public endpoints
    @GetMapping("/products")
    public ResponseEntity<PageResponse<ProductResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Sort sort = sortDir.equalsIgnoreCase("asc") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        PageResponse<ProductResponse> products;
        
        if (keyword != null && !keyword.isEmpty()) {
            products = productService.searchAllProducts(keyword, pageable);
        } else if (category != null && !category.isEmpty()) {
            products = productService.getProductsByCategory(category, pageable);
        } else {
            // Default to empty search to get all products
            products = productService.searchAllProducts("", pageable);
        }
        
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long productId) {
        return ResponseEntity.ok(productService.getProductById(productId));
    }
    
    // Shop owner endpoints
    @PostMapping("/shops/{shopId}/products")
    public ResponseEntity<ProductResponse> createProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @Valid @RequestBody ProductRequest request) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        ProductResponse createdProduct = productService.createProduct(shopId, request);
        return new ResponseEntity<>(createdProduct, HttpStatus.CREATED);
    }
    
    @PutMapping("/shops/{shopId}/products/{productId}")
    public ResponseEntity<ProductResponse> updateProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @Valid @RequestBody ProductRequest request) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        ProductResponse updatedProduct = productService.updateProduct(shopId, productId, request);
        return ResponseEntity.ok(updatedProduct);
    }
    
    @DeleteMapping("/shops/{shopId}/products/{productId}")
    public ResponseEntity<Void> deleteProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @PathVariable Long productId) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        productService.deleteProduct(shopId, productId);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/shops/{shopId}/products")
    public ResponseEntity<PageResponse<ProductResponse>> getShopProducts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        Sort sort = sortDir.equalsIgnoreCase("asc") ? 
                Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        PageResponse<ProductResponse> products;
        
        if (keyword != null && !keyword.isEmpty()) {
            products = productService.searchShopProducts(shopId, keyword, pageable);
        } else {
            products = productService.getShopProducts(shopId, pageable);
        }
        
        return ResponseEntity.ok(products);
    }
    
    @GetMapping("/shops/{shopId}/products/{productId}")
    public ResponseEntity<ProductResponse> getShopProduct(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @PathVariable Long productId) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        return ResponseEntity.ok(productService.getShopProductById(shopId, productId));
    }
    
    @GetMapping("/shops/{shopId}/categories")
    public ResponseEntity<List<String>> getShopCategories(
            @PathVariable Long shopId) {
        return ResponseEntity.ok(productService.getShopCategories(shopId));
    }
    
    @PatchMapping("/shops/{shopId}/products/{productId}/stock")
    public ResponseEntity<Void> updateProductStock(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @PathVariable Long productId,
            @RequestParam Integer quantity) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        productService.updateProductStock(shopId, productId, quantity);
        return ResponseEntity.ok().build();
    }
    
    // AI Consultation endpoint - optimized for minimal data transfer
    @GetMapping("/shops/{shopId}/products/consultation")
    public ResponseEntity<List<ProductConsultationResponse>> getProductsForAIConsultation(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        validateShopOwnership(userId, shopId);
        
        List<Product> products = productRepository.findByShopIdAndActiveTrue(shopId);
        List<ProductConsultationResponse> consultationResponses = 
                ProductConsultationResponse.fromEntities(products);
        
        return ResponseEntity.ok(consultationResponses);
    }
    
    // Helper method to validate shop ownership
    private void validateShopOwnership(Long userId, Long shopId) {
        if (!shopService.isShopOwner(userId, shopId)) {
            throw new SecurityException("User is not authorized to access this shop");
        }
    }
} 