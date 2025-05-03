package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.AccessTokenRequest;
import com.g18.assistant.dto.response.AccessTokenResponse;
import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import com.g18.assistant.mapper.AccessTokenMapper;
import com.g18.assistant.repository.AccessTokenRepository;
import com.g18.assistant.repository.ShopRepository;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.service.AccessTokenService;
import com.g18.assistant.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessTokenServiceImpl implements AccessTokenService {
    
    private final AccessTokenRepository accessTokenRepository;
    private final UserRepository userRepository;
    private final ShopRepository shopRepository;
    private final ShopService shopService;
    private final AccessTokenMapper accessTokenMapper;
    
    @Override
    @Transactional
    public AccessTokenResponse addAccessToken(String username, AccessTokenRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Validate that the shop belongs to the user
        Shop shop = shopService.validateUserShop(request.getShopId(), username);
        
        // Check if there's an existing active token for the same method and shop
        accessTokenRepository.findByUserAndMethodAndStatus(user, request.getMethod(), AccessToken.TokenStatus.ACTIVE)
                .ifPresent(token -> {
                    // Deactivate existing token
                    token.setStatus(AccessToken.TokenStatus.EXPIRED);
                    accessTokenRepository.save(token);
                });
        
        // Create and save new token
        AccessToken accessToken = accessTokenMapper.toEntity(request, user, shop);
        AccessToken savedToken = accessTokenRepository.save(accessToken);
        
        return accessTokenMapper.toResponse(savedToken);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AccessTokenResponse> getAllTokens(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return accessTokenRepository.findByUser(user).stream()
                .map(accessTokenMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AccessTokenResponse> getShopTokens(String username, Long shopId) {
        // Validate that the shop belongs to the user
        Shop shop = shopService.validateUserShop(shopId, username);
        
        return accessTokenRepository.findByShop(shop).stream()
                .map(accessTokenMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AccessTokenResponse> getActiveTokensByMethod(String username, AccessToken.TokenMethod method) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return accessTokenRepository.findByUserAndMethod(user, method).stream()
                .filter(token -> token.getStatus() == AccessToken.TokenStatus.ACTIVE)
                .map(accessTokenMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<AccessTokenResponse> getActiveShopTokensByMethod(String username, Long shopId, AccessToken.TokenMethod method) {
        // Validate that the shop belongs to the user
        Shop shop = shopService.validateUserShop(shopId, username);
        
        return accessTokenRepository.findByShopAndMethod(shop, method).stream()
                .filter(token -> token.getStatus() == AccessToken.TokenStatus.ACTIVE)
                .map(accessTokenMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public AccessTokenResponse getTokenById(Long tokenId, String username) {
        // Get the user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
                
        // Find the token
        AccessToken accessToken = accessTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Access token not found"));
        
        // Check if the token belongs to the user
        if (!accessToken.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You don't have permission to access this token");
        }
        
        return accessTokenMapper.toResponse(accessToken);
    }
    
    @Override
    @Transactional
    public AccessTokenResponse updateTokenStatus(Long tokenId, AccessToken.TokenStatus status, String username) {
        // Get the user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
                
        // Find the token
        AccessToken accessToken = accessTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Access token not found"));
        
        // Check if the token belongs to the user
        if (!accessToken.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You don't have permission to update this token");
        }
        
        accessToken.setStatus(status);
        AccessToken updatedToken = accessTokenRepository.save(accessToken);
        
        return accessTokenMapper.toResponse(updatedToken);
    }
    
    @Override
    @Transactional
    public void deleteToken(Long tokenId, String username) {
        // Get the user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
                
        // Find the token
        AccessToken accessToken = accessTokenRepository.findById(tokenId)
                .orElseThrow(() -> new IllegalArgumentException("Access token not found"));
        
        // Check if the token belongs to the user
        if (!accessToken.getUser().getId().equals(user.getId())) {
            throw new SecurityException("You don't have permission to delete this token");
        }
        
        accessTokenRepository.deleteById(tokenId);
    }
} 