package com.g18.assistant.controller;

import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.dto.request.UpdateOrderStatusRequest;
import com.g18.assistant.entity.Order.OrderStatus;
import com.g18.assistant.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    
    private final OrderService orderService;
    
    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderDTO orderDTO = orderService.createOrder(request);
        return new ResponseEntity<>(orderDTO, HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(@PathVariable Long id) {
        OrderDTO orderDTO = orderService.getOrderById(id);
        return ResponseEntity.ok(orderDTO);
    }
    
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByShopId(@PathVariable Long shopId) {
        List<OrderDTO> orders = orderService.getOrdersByShopId(shopId);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByCustomerId(@PathVariable Long customerId) {
        List<OrderDTO> orders = orderService.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/shop/{shopId}/status/{status}")
    public ResponseEntity<List<OrderDTO>> getOrdersByShopIdAndStatus(
            @PathVariable Long shopId,
            @PathVariable OrderStatus status) {
        List<OrderDTO> orders = orderService.getOrdersByShopIdAndStatus(shopId, status);
        return ResponseEntity.ok(orders);
    }
    
    @GetMapping("/shop/{shopId}/date-range")
    public ResponseEntity<List<OrderDTO>> getOrdersByDateRange(
            @PathVariable Long shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        List<OrderDTO> orders = orderService.getOrdersByDateRange(shopId, startDate, endDate);
        return ResponseEntity.ok(orders);
    }
    
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        OrderDTO updatedOrder = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(updatedOrder);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
} 