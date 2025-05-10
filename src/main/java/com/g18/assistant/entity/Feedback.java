package com.g18.assistant.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feedback")
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "id_customer", nullable = false)
    private Customer customer;
    
    @ManyToOne
    @JoinColumn(name = "id_product", nullable = false)
    private Product product;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @Column(nullable = false)
    private LocalDateTime time;
} 