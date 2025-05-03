package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "telegram_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramMessage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String username;
    
    @Column(nullable = false, length = 4000)
    private String messageText;
    
    @Column(nullable = false)
    private Long chatId;
    
    @Column(nullable = true, length = 512)
    private String fileUrl;
    
    @Column(nullable = true)
    private String fileType;
    
    @Column(nullable = false)
    private LocalDateTime receivedAt;
    
    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;
} 