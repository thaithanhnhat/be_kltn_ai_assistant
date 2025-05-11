package com.g18.assistant.controller;

import com.g18.assistant.dto.request.AccessTokenRequest;
import com.g18.assistant.dto.response.AccessTokenResponse;
import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.service.AccessTokenService;
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
@RequestMapping("/api/integration/tokens")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Integration Tokens", description = "Manage integration access tokens for external platforms")
public class AccessTokenController {
    
    private final AccessTokenService accessTokenService;
    
    @PostMapping
    @Operation(summary = "Add new access token", description = "Add a new access token for Facebook or Telegram integration")
    public ResponseEntity<AccessTokenResponse> addAccessToken(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AccessTokenRequest request) {
        
        String username = jwt.getSubject();
        AccessTokenResponse token = accessTokenService.addAccessToken(username, request);
        return ResponseEntity.ok(token);
    }
    
    @GetMapping
    @Operation(summary = "Get all tokens", description = "Get all integration tokens for the authenticated user")
    public ResponseEntity<List<AccessTokenResponse>> getAllTokens(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        List<AccessTokenResponse> tokens = accessTokenService.getAllTokens(username);
        return ResponseEntity.ok(tokens);
    }
    
    @GetMapping("/shop/{shopId}")
    @Operation(summary = "Get shop tokens", description = "Get all tokens for a specific shop")
    public ResponseEntity<?> getShopTokens(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId) {
        try {
            String username = jwt.getSubject();
            List<AccessTokenResponse> tokens = accessTokenService.getShopTokens(username, shopId);
            return ResponseEntity.ok(tokens);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get token by ID", description = "Get a specific integration token by ID if it belongs to the authenticated user")
    public ResponseEntity<?> getTokenById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        try {
            String username = jwt.getSubject();
            AccessTokenResponse token = accessTokenService.getTokenById(id, username);
            return ResponseEntity.ok(token);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @GetMapping("/method/{method}")
    @Operation(summary = "Get tokens by method", description = "Get active tokens for a specific integration method")
    public ResponseEntity<List<AccessTokenResponse>> getTokensByMethod(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable AccessToken.TokenMethod method) {
        
        String username = jwt.getSubject();
        List<AccessTokenResponse> tokens = accessTokenService.getActiveTokensByMethod(username, method);
        return ResponseEntity.ok(tokens);
    }
    
    @GetMapping("/shop/{shopId}/method/{method}")
    @Operation(summary = "Get shop tokens by method", description = "Get active tokens for a specific shop and method")
    public ResponseEntity<?> getShopTokensByMethod(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long shopId,
            @PathVariable AccessToken.TokenMethod method) {
        try {
            String username = jwt.getSubject();
            List<AccessTokenResponse> tokens = accessTokenService.getActiveShopTokensByMethod(username, shopId, method);
            return ResponseEntity.ok(tokens);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update token status", description = "Update the status of an access token if it belongs to the authenticated user")
    public ResponseEntity<?> updateTokenStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @RequestBody Map<String, String> statusMap) {
        
        AccessToken.TokenStatus status;
        try {
            status = AccessToken.TokenStatus.valueOf(statusMap.get("status"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status value"));
        }
        
        try {
            String username = jwt.getSubject();
            AccessTokenResponse token = accessTokenService.updateTokenStatus(id, status, username);
            return ResponseEntity.ok(token);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete token", description = "Delete an access token if it belongs to the authenticated user")
    public ResponseEntity<?> deleteToken(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        try {
            String username = jwt.getSubject();
            accessTokenService.deleteToken(id, username);
            return ResponseEntity.noContent().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
} 