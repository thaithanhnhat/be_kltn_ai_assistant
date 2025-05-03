package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import com.g18.assistant.mapper.ShopMapper;
import com.g18.assistant.repository.ShopRepository;
import com.g18.assistant.repository.UserRepository;
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
public class ShopServiceImpl implements ShopService {
    
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ShopMapper shopMapper;
    
    @Override
    @Transactional
    public ShopResponse createShop(String username, ShopRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Check if shop with the same name already exists for this user
        if (shopRepository.existsByNameAndUser(request.getName(), user)) {
            throw new IllegalArgumentException("A shop with this name already exists for this user");
        }
        
        // Create and save new shop
        Shop shop = shopMapper.toEntity(request, user);
        Shop savedShop = shopRepository.save(shop);
        
        return shopMapper.toResponse(savedShop);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ShopResponse> getUserShops(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return shopRepository.findByUser(user).stream()
                .map(shopMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<ShopResponse> getActiveShops(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return shopRepository.findByUserAndStatus(user, Shop.ShopStatus.ACTIVE).stream()
                .map(shopMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public ShopResponse getShopById(Long shopId, String username) {
        Shop shop = validateUserShop(shopId, username);
        return shopMapper.toResponse(shop);
    }
    
    @Override
    @Transactional
    public ShopResponse updateShop(Long shopId, String username, ShopRequest request) {
        Shop shop = validateUserShop(shopId, username);
        
        // Update shop properties
        shopMapper.updateEntity(shop, request);
        Shop updatedShop = shopRepository.save(shop);
        
        return shopMapper.toResponse(updatedShop);
    }
    
    @Override
    @Transactional
    public ShopResponse updateShopStatus(Long shopId, String username, Shop.ShopStatus status) {
        Shop shop = validateUserShop(shopId, username);
        
        shop.setStatus(status);
        Shop updatedShop = shopRepository.save(shop);
        
        return shopMapper.toResponse(updatedShop);
    }
    
    @Override
    @Transactional
    public void deleteShop(Long shopId, String username) {
        Shop shop = validateUserShop(shopId, username);
        shopRepository.delete(shop);
    }
    
    @Override
    public Shop validateUserShop(Long shopId, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return shopRepository.findByIdAndUser(shopId, user)
                .orElseThrow(() -> new SecurityException("You don't have permission to access this shop"));
    }
    
    @Override
    @Transactional(readOnly = true)
    public boolean isShopOwner(Long userId, Long shopId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        
        return shopRepository.existsByIdAndUser(shopId, user);
    }
} 