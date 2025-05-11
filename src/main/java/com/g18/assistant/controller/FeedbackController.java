package com.g18.assistant.controller;

import com.g18.assistant.dto.FeedbackDTO;
import com.g18.assistant.dto.request.CreateFeedbackRequest;
import com.g18.assistant.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {
    
    private final FeedbackService feedbackService;
    
    @PostMapping
    public ResponseEntity<FeedbackDTO> createFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        FeedbackDTO feedbackDTO = feedbackService.createFeedback(request);
        return new ResponseEntity<>(feedbackDTO, HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<FeedbackDTO> getFeedbackById(@PathVariable Long id) {
        FeedbackDTO feedbackDTO = feedbackService.getFeedbackById(id);
        return ResponseEntity.ok(feedbackDTO);
    }
    
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<FeedbackDTO>> getFeedbacksByShopId(@PathVariable Long shopId) {
        List<FeedbackDTO> feedbacks = feedbackService.getFeedbacksByShopId(shopId);
        return ResponseEntity.ok(feedbacks);
    }
    
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<FeedbackDTO>> getFeedbacksByCustomerId(@PathVariable Long customerId) {
        List<FeedbackDTO> feedbacks = feedbackService.getFeedbacksByCustomerId(customerId);
        return ResponseEntity.ok(feedbacks);
    }
    
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<FeedbackDTO>> getFeedbacksByProductId(@PathVariable Long productId) {
        List<FeedbackDTO> feedbacks = feedbackService.getFeedbacksByProductId(productId);
        return ResponseEntity.ok(feedbacks);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return ResponseEntity.noContent().build();
    }
} 