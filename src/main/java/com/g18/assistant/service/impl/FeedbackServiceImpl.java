package com.g18.assistant.service.impl;

import com.g18.assistant.dto.FeedbackDTO;
import com.g18.assistant.dto.request.CreateFeedbackRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Feedback;
import com.g18.assistant.entity.Product;
import com.g18.assistant.exception.ResourceNotFoundException;
import com.g18.assistant.mapper.FeedbackMapper;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.FeedbackRepository;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {
    
    private final FeedbackRepository feedbackRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final FeedbackMapper feedbackMapper;
    
    @Override
    public FeedbackDTO createFeedback(CreateFeedbackRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));
        
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));
        
        // Check if product belongs to customer's shop
        if (!product.getShop().getId().equals(customer.getShop().getId())) {
            throw new IllegalArgumentException("Product does not belong to the customer's shop");
        }
        
        Feedback feedback = feedbackMapper.toEntity(request, customer, product);
        Feedback savedFeedback = feedbackRepository.save(feedback);
        return feedbackMapper.toDTO(savedFeedback);
    }
    
    @Override
    public FeedbackDTO getFeedbackById(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));
        return feedbackMapper.toDTO(feedback);
    }
    
    @Override
    public List<FeedbackDTO> getFeedbacksByShopId(Long shopId) {
        List<Feedback> feedbacks = feedbackRepository.findAllByShopId(shopId);
        return feedbacks.stream()
                .map(feedbackMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FeedbackDTO> getFeedbacksByCustomerId(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        
        List<Feedback> feedbacks = feedbackRepository.findByCustomer(customer);
        return feedbacks.stream()
                .map(feedbackMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<FeedbackDTO> getFeedbacksByProductId(Long productId) {
        List<Feedback> feedbacks = feedbackRepository.findByProductIdOrderByTimeDesc(productId);
        return feedbacks.stream()
                .map(feedbackMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public void deleteFeedback(Long id) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found with id: " + id));
        feedbackRepository.delete(feedback);
    }
} 