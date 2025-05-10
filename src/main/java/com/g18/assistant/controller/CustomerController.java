package com.g18.assistant.controller;

import com.g18.assistant.dto.CustomerDTO;
import com.g18.assistant.dto.request.CreateCustomerRequest;
import com.g18.assistant.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {
    
    private final CustomerService customerService;
    
    @PostMapping
    public ResponseEntity<CustomerDTO> createCustomer(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerDTO customerDTO = customerService.createCustomer(request);
        return new ResponseEntity<>(customerDTO, HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<CustomerDTO> getCustomerById(@PathVariable Long id) {
        CustomerDTO customerDTO = customerService.getCustomerDTOById(id);
        return ResponseEntity.ok(customerDTO);
    }
    
    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<CustomerDTO>> getCustomersByShopId(@PathVariable Long shopId) {
        List<CustomerDTO> customers = customerService.getCustomersByShopId(shopId);
        return ResponseEntity.ok(customers);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<CustomerDTO> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CreateCustomerRequest request) {
        CustomerDTO updatedCustomer = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(updatedCustomer);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
} 