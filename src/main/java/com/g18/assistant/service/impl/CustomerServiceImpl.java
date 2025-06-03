package com.g18.assistant.service.impl;

import com.g18.assistant.dto.CustomerDTO;
import com.g18.assistant.dto.request.CreateCustomerRequest;
import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.exception.ResourceNotFoundException;
import com.g18.assistant.mapper.CustomerMapper;
import com.g18.assistant.repository.CustomerRepository;
import com.g18.assistant.repository.ShopRepository;
import com.g18.assistant.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    
    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final CustomerMapper customerMapper;
    
    // Regex patterns for information extraction
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+84|84|0)([3-9]\\d{8})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    
    @Override
    public List<CustomerDTO> getAllCustomers() {
        List<Customer> customers = customerRepository.findAll();
        return customers.stream()
                .map(customerMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public CustomerDTO getCustomerDTOById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        return customerMapper.toDTO(customer);
    }
    
    @Override
    public List<CustomerDTO> getCustomersByShopId(Long shopId) {
        // Verify shop exists
        shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + shopId));
        
        List<Customer> customers = customerRepository.findByShopId(shopId);
        return customers.stream()
                .map(customerMapper::toDTO)
                .collect(Collectors.toList());
    }
    
    @Override
    public CustomerDTO createCustomer(CreateCustomerRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + request.getShopId()));
        
        Customer customer = customerMapper.toEntity(request, shop);
        
        Customer savedCustomer = customerRepository.save(customer);
        return customerMapper.toDTO(savedCustomer);
    }
    
    @Override
    public CustomerDTO updateCustomer(Long id, CreateCustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        
        // Update customer fields manually
        customer.setFullname(request.getFullname());
        customer.setAddress(request.getAddress());
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        
        if (request.getShopId() != null) {
            Shop shop = shopRepository.findById(request.getShopId())
                    .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + request.getShopId()));
            customer.setShop(shop);
        }
        
        Customer savedCustomer = customerRepository.save(customer);
        return customerMapper.toDTO(savedCustomer);
    }
    
    @Override
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
        customerRepository.delete(customer);
    }
    
    @Override
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found with id: " + id));
    }
    
    @Override
    public Customer findByPhoneAndShopId(String phone, Long shopId) {
        return customerRepository.findByPhoneAndShopId(phone, shopId).orElse(null);
    }
    
    @Override
    public Customer findByEmailAndShopId(String email, Long shopId) {
        return customerRepository.findByEmailAndShopId(email, shopId).orElse(null);
    }
    
    @Override
    @Transactional
    public Customer updateCustomerInfo(Long customerId, Map<String, String> customerInfo) {
        Customer customer = getCustomerById(customerId);
        
        if (customerInfo.containsKey("name") && customerInfo.get("name") != null) {
            customer.setFullname(customerInfo.get("name"));
        }
        
        if (customerInfo.containsKey("address") && customerInfo.get("address") != null) {
            customer.setAddress(customerInfo.get("address"));
        }
        
        if (customerInfo.containsKey("phone") && customerInfo.get("phone") != null) {
            customer.setPhone(customerInfo.get("phone"));
        }
        
        if (customerInfo.containsKey("email") && customerInfo.get("email") != null) {
            customer.setEmail(customerInfo.get("email"));
        }
        
        return customerRepository.save(customer);
    }
      @Override
    @Transactional
    public Customer createNewCustomer(Long shopId, String phone, String name, String email) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found with id: " + shopId));
        
        Customer customer = new Customer();
        customer.setShop(shop);
        customer.setPhone(phone != null ? phone : "unknown");
        
        // Improve customer name logic
        String customerName = "New Customer"; // default fallback
        if (name != null && !name.trim().isEmpty()) {
            customerName = name.trim();
        } else if (email != null && email.startsWith("telegram_")) {
            // Extract user ID from telegram email pattern for better fallback
            String userId = email.replace("telegram_", "").replace("@example.com", "");
            customerName = "Khách hàng Telegram #" + userId;
        } else if (email != null && email.startsWith("facebook_")) {
            // Extract user ID from facebook email pattern for better fallback
            String userId = email.replace("facebook_", "").replace("@example.com", "");
            customerName = "Khách hàng Facebook #" + userId;
        }
        
        customer.setFullname(customerName);
        customer.setEmail(email != null ? email : "unknown@example.com");
        customer.setAddress("Đang cập nhật"); // Temporary placeholder
        
        return customerRepository.save(customer);
    }
    
    @Override
    public Set<String> getMissingInformation(Long customerId) {
        Customer customer = getCustomerById(customerId);
        Set<String> missingFields = new HashSet<>();
        
        // Check for missing or placeholder information
        if (customer.getFullname() == null || customer.getFullname().isEmpty() || 
            customer.getFullname().equals("New Customer")) {
            missingFields.add("name");
        }
        
        if (customer.getAddress() == null || customer.getAddress().isEmpty() ||
            customer.getAddress().equals("Đang cập nhật")) {
            missingFields.add("address");
        }
        
        if (customer.getPhone() == null || customer.getPhone().isEmpty() ||
            customer.getPhone().equals("unknown")) {
            missingFields.add("phone");
        }
        
        if (customer.getEmail() == null || customer.getEmail().isEmpty() || 
            customer.getEmail().equals("unknown@example.com") ||
            customer.getEmail().startsWith("telegram_") || 
            customer.getEmail().startsWith("facebook_")) {
            missingFields.add("email");
        }
        
        return missingFields;
    }
    
    @Override
    public Map<String, String> extractCustomerInfoFromMessage(String message) {
        Map<String, String> extractedInfo = new HashMap<>();
        
        // Extract phone number
        Matcher phoneMatcher = PHONE_PATTERN.matcher(message);
        if (phoneMatcher.find()) {
            String phone = phoneMatcher.group();
            if (phone.startsWith("+84")) {
                phone = "0" + phone.substring(3);
            } else if (phone.startsWith("84")) {
                phone = "0" + phone.substring(2);
            }
            extractedInfo.put("phone", phone);
        }
        
        // Extract email
        Matcher emailMatcher = EMAIL_PATTERN.matcher(message);
        if (emailMatcher.find()) {
            extractedInfo.put("email", emailMatcher.group());
        }
        
        // Extract address
        if (message.toLowerCase().contains("địa chỉ") || 
            message.toLowerCase().contains("address") || 
            message.toLowerCase().contains("giao hàng")) {
            
            // Try to extract address after common markers
            String[] addressMarkers = {"địa chỉ", "address", "giao hàng tới", "giao đến", "ship đến"};
            for (String marker : addressMarkers) {
                int index = message.toLowerCase().indexOf(marker);
                if (index >= 0) {
                    String afterMarker = message.substring(index + marker.length()).trim();
                    // Extract up to the end or until a period or new line
                    int endIndex = Math.min(
                        afterMarker.indexOf('.') > 0 ? afterMarker.indexOf('.') : afterMarker.length(),
                        afterMarker.indexOf('\n') > 0 ? afterMarker.indexOf('\n') : afterMarker.length()
                    );
                    String potentialAddress = afterMarker.substring(0, endIndex).trim();
                    if (!potentialAddress.isEmpty() && potentialAddress.length() > 5) {
                        extractedInfo.put("address", potentialAddress);
                        break;
                    }
                }
            }
        }
        
        return extractedInfo;
    }
} 