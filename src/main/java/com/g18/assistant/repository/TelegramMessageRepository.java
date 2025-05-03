package com.g18.assistant.repository;

import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TelegramMessageRepository extends JpaRepository<TelegramMessage, Long> {
    
    List<TelegramMessage> findByShop(Shop shop);
    
    List<TelegramMessage> findByShopAndProcessed(Shop shop, Boolean processed);
    
    List<TelegramMessage> findByChatId(Long chatId);
    
    List<TelegramMessage> findByShopOrderByReceivedAtDesc(Shop shop);
} 