package com.g18.assistant.service.impl;

import com.g18.assistant.service.ConversationHistoryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ConversationHistoryServiceImpl implements ConversationHistoryService {
    // Key: shopId:customerId, Value: List<ConversationEntry>
    private final Map<String, LinkedList<ConversationEntry>> historyMap = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 20;
    private static final long SESSION_TIMEOUT_HOURS = 6; // Session timeout in hours

    private String key(Long shopId, String customerId) {
        return shopId + ":" + (customerId == null ? "_" : customerId);
    }

    @Override
    public void addMessage(Long shopId, String customerId, String role, String message) {
        String key = key(shopId, customerId);
        historyMap.putIfAbsent(key, new LinkedList<>());
        LinkedList<ConversationEntry> list = historyMap.get(key);
        synchronized (list) {
            // Check if we need to start a new session based on time gap
            boolean startNewSession = false;
            if (!list.isEmpty()) {
                LocalDateTime lastMessageTime = list.getLast().timestamp;
                if (lastMessageTime != null && 
                    ChronoUnit.HOURS.between(lastMessageTime, LocalDateTime.now()) >= SESSION_TIMEOUT_HOURS) {
                    startNewSession = true;
                }
            }
            
            // If starting a new session, clear previous conversation
            if (startNewSession) {
                list.clear();
            }
            
            list.add(new ConversationEntry(role, message, LocalDateTime.now()));
            if (list.size() > MAX_HISTORY) {
                list.removeFirst();
            }
        }
    }

    @Override
    public List<ConversationEntry> getRecentHistory(Long shopId, String customerId, int limit) {
        String key = key(shopId, customerId);
        LinkedList<ConversationEntry> list = historyMap.getOrDefault(key, new LinkedList<>());
        synchronized (list) {
            // Filter out old messages that would be in a different session
            LocalDateTime now = LocalDateTime.now();
            list.removeIf(entry -> 
                entry.timestamp != null && 
                ChronoUnit.HOURS.between(entry.timestamp, now) >= SESSION_TIMEOUT_HOURS);
                
            int from = Math.max(0, list.size() - limit);
            return new ArrayList<>(list.subList(from, list.size()));
        }
    }
    
    @Override
    public void clearHistory(Long shopId, String customerId) {
        String key = key(shopId, customerId);
        historyMap.remove(key);
    }
} 