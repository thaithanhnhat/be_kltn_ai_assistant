package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "access_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;
    
    @Column(nullable = false, length = 512)
    private String accessToken;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenMethod method;
    
    public enum TokenStatus {
        ACTIVE, EXPIRED, REVOKED
    }
    
    public enum TokenMethod {
        TELEGRAM, FACEBOOK
    }
} 