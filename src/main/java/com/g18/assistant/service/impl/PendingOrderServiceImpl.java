package com.g18.assistant.service.impl;

import com.g18.assistant.service.PendingOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class PendingOrderServiceImpl implements PendingOrderService {
    
    private final Map<String, PendingOrderInfo> pendingOrders = new ConcurrentHashMap<>();
    
    @Override
    public void storePendingOrder(String customerKey, Long customerId, Long productId, Integer quantity, OrderSource source) {
        storePendingOrder(customerKey, customerId, productId, quantity, null, source);
    }
    
    @Override
    public void storePendingOrder(String customerKey, Long customerId, Long productId, Integer quantity, String note, OrderSource source) {
        PendingOrderInfo orderInfo = new PendingOrderInfo(customerId, productId, quantity, source);
        orderInfo.setNote(note);
        pendingOrders.put(customerKey, orderInfo);
        log.info("Stored pending order for customer key: {} with product ID: {} and quantity: {}", 
                customerKey, productId, quantity);
    }
    
    @Override
    public PendingOrderInfo getPendingOrder(String customerKey) {
        return pendingOrders.get(customerKey);
    }
    
    @Override
    public void removePendingOrder(String customerKey) {
        PendingOrderInfo removed = pendingOrders.remove(customerKey);
        if (removed != null) {
            log.info("Removed pending order for customer key: {}", customerKey);
        }
    }
    
    @Override
    public boolean hasPendingOrder(String customerKey) {
        return pendingOrders.containsKey(customerKey);
    }
    
    @Override
    public Map<String, PendingOrderInfo> getAllPendingOrders() {
        return Map.copyOf(pendingOrders);
    }
    
    @Override
    public void clearAllPendingOrders() {
        int size = pendingOrders.size();
        pendingOrders.clear();
        log.info("Cleared {} pending orders", size);
    }
}