package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "facebook_message_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacebookMessageLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "shop_id")
    private Long shopId;
    
    @Column(name = "page_id", length = 100)
    private String pageId;
    
    @Column(name = "sender_id", length = 100)
    private String senderId;
    
    @Column(name = "recipient_id", length = 100)
    private String recipientId;
    
    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;
    
    @Column(name = "message_type", length = 50)
    private String messageType;
    
    @Column(name = "status", length = 20)
    private String status;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "ai_intent", length = 100)
    private String aiIntent;
    
    @Column(name = "ai_confidence")
    private Double aiConfidence;
    
    @Column(name = "processing_time_ms")
    private Long processingTimeMs;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}