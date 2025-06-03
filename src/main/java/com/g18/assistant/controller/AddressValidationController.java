package com.g18.assistant.controller;

import com.g18.assistant.dto.response.AddressValidationResponse;
import com.g18.assistant.service.AddressValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
@Slf4j
public class AddressValidationController {
    
    private final AddressValidationService addressValidationService;
    
    /**
     * Validate a given address
     */
    @PostMapping("/validate")
    public ResponseEntity<AddressValidationResponse> validateAddress(@RequestParam String address) {
        log.info("Validating address: {}", address);
        AddressValidationResponse response = addressValidationService.validateAddress(address);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Check if address is complete
     */
    @GetMapping("/complete")
    public ResponseEntity<Boolean> isAddressComplete(@RequestParam String address) {
        boolean isComplete = addressValidationService.isAddressComplete(address);
        return ResponseEntity.ok(isComplete);
    }
    
    /**
     * Standardize an address
     */
    @PostMapping("/standardize")
    public ResponseEntity<String> standardizeAddress(@RequestParam String address) {
        String standardized = addressValidationService.standardizeAddress(address);
        return ResponseEntity.ok(standardized);
    }
    
    /**
     * Extract address components
     */
    @PostMapping("/extract")
    public ResponseEntity<AddressValidationResponse> extractComponents(@RequestParam String address) {
        AddressValidationResponse response = addressValidationService.extractAddressComponents(address);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Check if address contains Vietnamese keywords
     */
    @GetMapping("/vietnamese-keywords")
    public ResponseEntity<Boolean> containsVietnameseKeywords(@RequestParam String address) {
        boolean containsKeywords = addressValidationService.containsVietnameseAddressKeywords(address);
        return ResponseEntity.ok(containsKeywords);
    }
}
