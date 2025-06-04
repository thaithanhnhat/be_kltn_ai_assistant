package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.dto.response.ProductResponse;
import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.FeedbackDTO;
import com.g18.assistant.dto.CustomerDTO;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Product;
import com.g18.assistant.mapper.ShopMapper;
import com.g18.assistant.repository.ShopRepository;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.repository.TelegramMessageRepository;
import com.g18.assistant.repository.FacebookAccessTokenRepository;
import com.g18.assistant.repository.AccessTokenRepository;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.*;
import jakarta.persistence.EntityNotFoundException;
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
      // Services for cascade deletion
    private final ProductService productService;
    private final CustomerService customerService;
    private final OrderService orderService;
    private final FeedbackService feedbackService;
    private final ConversationHistoryService conversationHistoryService;
    private final PendingOrderService pendingOrderService;
      // Repositories for direct deletion
    private final TelegramMessageRepository telegramMessageRepository;
    private final FacebookAccessTokenRepository facebookAccessTokenRepository;
    private final AccessTokenRepository accessTokenRepository;
    private final ProductRepository productRepository;
    
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
        
        log.info("Starting cascade deletion for shop ID: {}", shopId);
        
        try {            // 1. Clear conversation history for all customers of this shop
            log.info("Clearing conversation history for shop ID: {}", shopId);
            // We need to get all customers first to clear their conversation histories
            List<CustomerDTO> customerDTOs = customerService.getCustomersByShopId(shopId);            for (CustomerDTO customerDTO : customerDTOs) {
                try {
                    conversationHistoryService.clearHistory(shopId, customerDTO.getEmail());
                } catch (Exception e) {
                    log.warn("Failed to clear conversation history for customer {}: {}", customerDTO.getId(), e.getMessage());
                }
            }
            
            // 2. Clear all pending orders (global clear - affects all shops)
            log.info("Clearing pending orders for shop ID: {}", shopId);
            try {
                pendingOrderService.clearAllPendingOrders();
            } catch (Exception e) {
                log.warn("Failed to clear pending orders: {}", e.getMessage());
            }
            
            // 3. Delete all orders (this will also restore product stock)
            log.info("Deleting orders for shop ID: {}", shopId);
            List<OrderDTO> orders = orderService.getOrdersByShopId(shopId);
            for (OrderDTO order : orders) {
                try {
                    orderService.deleteOrder(order.getId());
                } catch (Exception e) {
                    log.warn("Failed to delete order {}: {}", order.getId(), e.getMessage());
                }
            }
              // 4. Delete all feedbacks (through customers deletion will handle this)
            log.info("Deleting feedbacks for shop ID: {}", shopId);
            for (CustomerDTO customerDTO : customerDTOs) {
                try {
                    List<FeedbackDTO> feedbacks = feedbackService.getFeedbacksByCustomerId(customerDTO.getId());
                    for (FeedbackDTO feedback : feedbacks) {
                        feedbackService.deleteFeedback(feedback.getId());
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete feedbacks for customer {}: {}", customerDTO.getId(), e.getMessage());
                }
            }
              // 5. Delete all customers (this will cascade to their orders and feedbacks)
            log.info("Deleting customers for shop ID: {}", shopId);
            for (CustomerDTO customerDTO : customerDTOs) {
                try {
                    customerService.deleteCustomer(customerDTO.getId());
                } catch (Exception e) {
                    log.warn("Failed to delete customer {}: {}", customerDTO.getId(), e.getMessage());
                }
            }
              // 6. Delete all products (hard delete for cascade)
            log.info("Deleting products for shop ID: {}", shopId);
            try {
                List<Product> products = productRepository.findByShopId(shopId);
                for (Product product : products) {
                    productRepository.delete(product);
                }
                log.info("Hard deleted {} products for shop ID: {}", products.size(), shopId);
            } catch (Exception e) {
                log.warn("Failed to delete products: {}", e.getMessage());
            }// 7. Delete all access tokens directly via repository
            log.info("Deleting access tokens for shop ID: {}", shopId);
            try {
                var accessTokens = accessTokenRepository.findByShop(shop);
                for (var token : accessTokens) {
                    accessTokenRepository.delete(token);
                }
            } catch (Exception e) {
                log.warn("Failed to delete access tokens: {}", e.getMessage());
            }
              // 8. Delete Telegram messages directly via repository
            log.info("Deleting Telegram messages for shop ID: {}", shopId);
            try {
                var telegramMessages = telegramMessageRepository.findByShop(shop);
                for (var message : telegramMessages) {
                    telegramMessageRepository.delete(message);
                }
            } catch (Exception e) {
                log.warn("Failed to delete Telegram messages: {}", e.getMessage());
            }
            
            // 9. Delete Facebook access tokens directly via repository
            log.info("Deleting Facebook access tokens for shop ID: {}", shopId);
            try {
                facebookAccessTokenRepository.findByShopId(shopId).ifPresent(facebookAccessTokenRepository::delete);
            } catch (Exception e) {
                log.warn("Failed to delete Facebook access tokens: {}", e.getMessage());
            }
            
            // 10. Finally, delete the shop itself
            log.info("Deleting shop ID: {}", shopId);
            shopRepository.delete(shop);
            
            log.info("Successfully completed cascade deletion for shop ID: {}", shopId);
            
        } catch (Exception e) {
            log.error("Error during cascade deletion for shop ID {}: {}", shopId, e.getMessage(), e);
            throw new RuntimeException("Failed to delete shop and related data", e);
        }
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
    
    @Override
    @Transactional(readOnly = true)
    public Shop getShopByIdForBotServices(Long shopId) {
        return shopRepository.findById(shopId)
                .orElseThrow(() -> new EntityNotFoundException("Shop not found with id: " + shopId));
    }
} 