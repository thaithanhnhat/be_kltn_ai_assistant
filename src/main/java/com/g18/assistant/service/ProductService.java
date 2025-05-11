package com.g18.assistant.service;

import com.g18.assistant.dto.request.ProductRequest;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {
    
    ProductResponse createProduct(Long shopId, ProductRequest request);
    
    ProductResponse updateProduct(Long shopId, Long productId, ProductRequest request);
    
    ProductResponse getProductById(Long productId);
    
    ProductResponse getShopProductById(Long shopId, Long productId);
    
    void deleteProduct(Long shopId, Long productId);
    
    PageResponse<ProductResponse> getShopProducts(Long shopId, Pageable pageable);
    
    PageResponse<ProductResponse> searchShopProducts(Long shopId, String keyword, Pageable pageable);
    
    PageResponse<ProductResponse> searchAllProducts(String keyword, Pageable pageable);
    
    PageResponse<ProductResponse> getProductsByCategory(String category, Pageable pageable);
    
    List<String> getShopCategories(Long shopId);
    
    void updateProductStock(Long shopId, Long productId, Integer quantity);
} 