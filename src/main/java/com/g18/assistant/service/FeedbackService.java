package com.g18.assistant.service;

import com.g18.assistant.dto.FeedbackDTO;
import com.g18.assistant.dto.request.CreateFeedbackRequest;

import java.util.List;

public interface FeedbackService {
    FeedbackDTO createFeedback(CreateFeedbackRequest request);
    FeedbackDTO getFeedbackById(Long id);
    List<FeedbackDTO> getFeedbacksByShopId(Long shopId);
    List<FeedbackDTO> getFeedbacksByCustomerId(Long customerId);
    List<FeedbackDTO> getFeedbacksByProductId(Long productId);
    void deleteFeedback(Long id);
} 