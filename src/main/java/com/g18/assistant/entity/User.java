package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String fullname;
    
    private LocalDate birthdate;
    
    @Builder.Default
    private Double balance = 0.0;
    
    @Builder.Default
    private Boolean isAdmin = false;
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;
    
    @Builder.Default
    private Boolean verified = false;
    
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Shop> shops;
    
    public enum UserStatus {
        ACTIVE, INACTIVE, BLOCKED
    }
} 