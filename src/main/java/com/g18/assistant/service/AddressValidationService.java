package com.g18.assistant.service;

import com.g18.assistant.dto.response.AddressValidationResponse;

/**
 * Service interface for address validation
 * Provides methods to validate and standardize addresses
 */
public interface AddressValidationService {
    
    /**
     * Validates if the given address is a valid Vietnamese address
     * 
     * @param address The address string to validate
     * @return AddressValidationResponse containing validation result and suggestions
     */
    AddressValidationResponse validateAddress(String address);
    
    /**
     * Validates if the given address is complete with all required components
     * 
     * @param address The address to check completeness
     * @return true if address has all required components, false otherwise
     */
    boolean isAddressComplete(String address);
    
    /**
     * Standardizes an address to a consistent format
     * 
     * @param address The raw address string
     * @return Standardized address string
     */
    String standardizeAddress(String address);
    
    /**
     * Extracts address components (street, ward, district, city) from address string
     * 
     * @param address The address string to parse
     * @return AddressValidationResponse with extracted components
     */
    AddressValidationResponse extractAddressComponents(String address);
    
    /**
     * Validates address format and checks for common issues
     * 
     * @param address The address to validate
     * @return AddressValidationResponse with validation details and suggestions
     */
    AddressValidationResponse validateAddressFormat(String address);
    
    /**
     * Checks if address contains necessary keywords for Vietnamese addressing
     * 
     * @param address The address string to check
     * @return true if contains proper Vietnamese address keywords
     */
    boolean containsVietnameseAddressKeywords(String address);
}