package com.g18.assistant.service;

import com.g18.assistant.dto.CustomerDTO;
import com.g18.assistant.dto.request.CreateCustomerRequest;
import com.g18.assistant.entity.Customer;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface CustomerService {
    List<CustomerDTO> getAllCustomers();
    CustomerDTO getCustomerDTOById(Long id);
    List<CustomerDTO> getCustomersByShopId(Long shopId);
    CustomerDTO createCustomer(CreateCustomerRequest request);
    CustomerDTO updateCustomer(Long id, CreateCustomerRequest request);
    void deleteCustomer(Long id);
    
    // New methods for entity-based operations
    Customer getCustomerById(Long id);
    Customer findByPhoneAndShopId(String phone, Long shopId);
    Customer findByEmailAndShopId(String email, Long shopId);
    Customer updateCustomerInfo(Long customerId, Map<String, String> customerInfo);
    Customer createNewCustomer(Long shopId, String phone, String name, String email);
    Set<String> getMissingInformation(Long customerId);
    Map<String, String> extractCustomerInfoFromMessage(String message);
} 