package com.g18.assistant.service.impl;

import com.g18.assistant.dto.OrderDTO;
import com.g18.assistant.dto.request.CreateOrderRequest;
import com.g18.assistant.dto.request.UpdateOrderStatusRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Order;
import com.g18.assistant.entity.Order.OrderStatus;
import com.g18.assistant.entity.Product;
import com.g18.assistant.exception.ResourceNotFoundException;
import com.g18.assistant.mapper.OrderMapper;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.OrderRepository;
import com.g18.assistant.repository.ProductRepository;
import com.g18.assistant.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    
    @Override
    @Transactional
    public OrderDTO createOrder(CreateOrderRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + request.getCustomerId()));
        
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));
        
        // Check if product belongs to customer's shop
        if (!product.getShop().getId().equals(customer.getShop().getId())) {
            throw new IllegalArgumentException("Product does not belong to the customer's shop");
        }
        
        // Check if product has enough stock
        if (product.getStock() < request.getQuantity()) {
            throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
        }
        
        // Reduce product stock
        product.setStock(product.getStock() - request.getQuantity());
        productRepository.save(product);
        
        Order order = orderMapper.toEntity(request, customer, product);
        Order savedOrder = orderRepository.save(order);
        return orderMapper.toDTO(savedOrder);
    }
    
    @Override
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return orderMapper.toDTO(order);
    }
    
    @Override
    public List<OrderDTO> getOrdersByShopId(Long shopId) {
        List<Order> orders = orderRepository.findAllByShopId(shopId);
        return orders.stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderDTO> getOrdersByCustomerId(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + customerId));
        
        List<Order> orders = orderRepository.findByCustomer(customer);
        return orders.stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderDTO> getOrdersByShopIdAndStatus(Long shopId, OrderStatus status) {
        List<Order> orders = orderRepository.findAllByShopIdAndStatus(shopId, status);
        return orders.stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<OrderDTO> getOrdersByDateRange(Long shopId, LocalDateTime startDate, LocalDateTime endDate) {
        List<Order> orders = orderRepository.findAllByShopIdAndCreatedAtBetween(shopId, startDate, endDate);
        return orders.stream()
                .map(orderMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public OrderDTO updateOrderStatus(Long id, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        
        // If order was cancelled and now it's being confirmed, check stock again
        if (order.getStatus() == OrderStatus.CANCELLED && request.getStatus() == OrderStatus.CONFIRMED) {
            Product product = order.getProduct();
            if (product.getStock() < order.getQuantity()) {
                throw new IllegalArgumentException("Not enough stock for product: " + product.getName());
            }
            
            // Reduce product stock again
            product.setStock(product.getStock() - order.getQuantity());
            productRepository.save(product);
        }
        
        // If order is being cancelled, restore the product stock
        if (request.getStatus() == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            Product product = order.getProduct();
            product.setStock(product.getStock() + order.getQuantity());
            productRepository.save(product);
        }
        
        order.setStatus(request.getStatus());
        Order updatedOrder = orderRepository.save(order);
        return orderMapper.toDTO(updatedOrder);
    }
    
    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        
        // If order is not cancelled, restore the product stock
        if (order.getStatus() != OrderStatus.CANCELLED) {
            Product product = order.getProduct();
            product.setStock(product.getStock() + order.getQuantity());
            productRepository.save(product);
        }
        
        orderRepository.delete(order);
    }
} 