package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_customer")
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "id_shop", nullable = false)
    private Shop shop;
    
    @Column(nullable = false)
    private String fullname;
    
    @Column(nullable = false)
    private String address;
    
    @Column(nullable = false)
    private String phone;
    
    @Column(nullable = false)
    private String email;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Order> orders;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL)
    private List<Feedback> feedbacks;
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
} 