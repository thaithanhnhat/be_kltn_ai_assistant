package com.g18.assistant.controller;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shops")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Shops", description = "Manage user shops")
public class ShopController {
    
    private final ShopService shopService;
    
    @PostMapping
    @Operation(summary = "Create new shop", description = "Create a new shop for the authenticated user")
    public ResponseEntity<ShopResponse> createShop(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ShopRequest request) {
        
        String username = jwt.getSubject();
        ShopResponse shop = shopService.createShop(username, request);
        return ResponseEntity.ok(shop);
    }
    
    @GetMapping
    @Operation(summary = "Get all shops", description = "Get all shops for the authenticated user")
    public ResponseEntity<List<ShopResponse>> getUserShops(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        List<ShopResponse> shops = shopService.getUserShops(username);
        return ResponseEntity.ok(shops);
    }
    
    @GetMapping("/active")
    @Operation(summary = "Get active shops", description = "Get all active shops for the authenticated user")
    public ResponseEntity<List<ShopResponse>> getActiveShops(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        List<ShopResponse> shops = shopService.getActiveShops(username);
        return ResponseEntity.ok(shops);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get shop by ID", description = "Get a specific shop by ID if it belongs to the authenticated user")
    public ResponseEntity<?> getShopById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        try {
            String username = jwt.getSubject();
            ShopResponse shop = shopService.getShopById(id, username);
            return ResponseEntity.ok(shop);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update shop", description = "Update a shop if it belongs to the authenticated user")
    public ResponseEntity<?> updateShop(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ShopRequest request) {
        try {
            String username = jwt.getSubject();
            ShopResponse shop = shopService.updateShop(id, username, request);
            return ResponseEntity.ok(shop);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update shop status", description = "Update a shop's status if it belongs to the authenticated user")
    public ResponseEntity<?> updateShopStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody Map<String, String> statusMap) {
        
        Shop.ShopStatus status;
        try {
            status = Shop.ShopStatus.valueOf(statusMap.get("status"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        }
        
        try {
            String username = jwt.getSubject();
            ShopResponse shop = shopService.updateShopStatus(id, username, status);
            return ResponseEntity.ok(shop);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete shop", description = "Delete a shop if it belongs to the authenticated user")
    public ResponseEntity<?> deleteShop(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        try {
            String username = jwt.getSubject();
            shopService.deleteShop(id, username);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
} 