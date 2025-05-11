package com.g18.assistant.service;

import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.dto.request.UpdateOrderStatusRequest;
import com.g18.assistant.entity.Order.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderService {
    OrderDTO createOrder(CreateOrderRequest request);
    OrderDTO getOrderById(Long id);
    List<OrderDTO> getOrdersByShopId(Long shopId);
    List<OrderDTO> getOrdersByCustomerId(Long customerId);
    List<OrderDTO> getOrdersByShopIdAndStatus(Long shopId, OrderStatus status);
    List<OrderDTO> getOrdersByDateRange(Long shopId, LocalDateTime startDate, LocalDateTime endDate);
    OrderDTO updateOrderStatus(Long id, UpdateOrderStatusRequest request);
    void deleteOrder(Long id);
} 