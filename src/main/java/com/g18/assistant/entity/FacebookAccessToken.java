package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "facebook_access_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacebookAccessToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long shopId;
    
    @Column(name = "page_id")
    private String pageId;
    
    @Column(length = 2000, nullable = false)
    private String accessToken;
    
    @Column
    private String verifyToken;
    
    @Column
    private String webhookUrl;
    
    @Column(name = "is_active")
    private boolean active;
    
    @Column
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 