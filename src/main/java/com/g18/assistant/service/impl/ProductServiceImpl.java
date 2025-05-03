package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.ProductRequest;
import com.g18.assistant.dto.response.PageResponse;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.entity.Product;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.repository.ShopRepository;
import com.g18.assistant.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    @Override
    @Transactional
    public ProductResponse createProduct(Long shopId, ProductRequest request) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found with id: " + shopId));
        
        Product product = Product.builder()
                .shop(shop)
                .name(request.getName())
                .price(request.getPrice())
                .description(request.getDescription())
                .category(request.getCategory())
                .stock(request.getStock())
                .imageBase64(request.getImageBase64())
                .customFields(request.getCustomFields())
                .build();
        
        Product savedProduct = productRepository.save(product);
        log.info("Created new product with id: {} for shop: {}", savedProduct.getId(), shopId);
        
        return ProductResponse.fromEntity(savedProduct);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(Long shopId, Long productId, ProductRequest request) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " for shop: " + shopId));
        
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setDescription(request.getDescription());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());
        if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
            product.setImageBase64(request.getImageBase64());
        }
        product.setCustomFields(request.getCustomFields());
        
        Product updatedProduct = productRepository.save(product);
        log.info("Updated product with id: {} for shop: {}", productId, shopId);
        
        return ProductResponse.fromEntity(updatedProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId));
        
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getShopProductById(Long shopId, Long productId) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " for shop: " + shopId));
        
        return ProductResponse.fromEntity(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long shopId, Long productId) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " for shop: " + shopId));
        
        // Soft delete by setting active to false
        product.setActive(false);
        productRepository.save(product);
        log.info("Soft deleted product with id: {} for shop: {}", productId, shopId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getShopProducts(Long shopId, Pageable pageable) {
        Page<Product> productPage = productRepository.findByShopIdAndActiveTrue(shopId, pageable);
        
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntityWithoutImage);
        
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchShopProducts(Long shopId, String keyword, Pageable pageable) {
        Page<Product> productPage = productRepository.searchProductsByShop(shopId, keyword, pageable);
        
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntityWithoutImage);
        
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchAllProducts(String keyword, Pageable pageable) {
        Page<Product> productPage = productRepository.searchAllActiveProducts(keyword, pageable);
        
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntityWithoutImage);
        
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProductsByCategory(String category, Pageable pageable) {
        Page<Product> productPage = productRepository.findByCategoryAndActiveTrue(category, pageable);
        
        Page<ProductResponse> responsePage = productPage.map(ProductResponse::fromEntityWithoutImage);
        
        return PageResponse.from(responsePage);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getShopCategories(Long shopId) {
        return productRepository.findAllCategoriesByShopId(shopId);
    }

    @Override
    @Transactional
    public void updateProductStock(Long shopId, Long productId, Integer quantity) {
        Product product = productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: " + productId + " for shop: " + shopId));
        
        // Update stock
        product.setStock(product.getStock() + quantity);
        
        // If stock becomes negative, set to 0
        if (product.getStock() < 0) {
            product.setStock(0);
        }
        
        productRepository.save(product);
        log.info("Updated stock for product id: {}, new stock: {}", productId, product.getStock());
    }
} 