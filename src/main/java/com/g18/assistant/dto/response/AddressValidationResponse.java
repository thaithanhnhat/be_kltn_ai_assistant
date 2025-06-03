package com.g18.assistant.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressValidationResponse {
    
    /**
     * Whether the address is valid
     */
    private boolean isValid;
    
    /**
     * Confidence score of validation (0.0 - 1.0)
     */
    private double confidence;
    
    /**
     * The standardized address
     */
    private String standardizedAddress;
    
    /**
     * Original address that was validated
     */
    private String originalAddress;
    
    /**
     * Address components extracted from the address
     */
    private AddressComponents components;
    
    /**
     * List of validation errors or issues found
     */
    private List<String> validationErrors;
    
    /**
     * Suggestions for improving the address
     */
    private List<String> suggestions;
    
    /**
     * Missing components that are required
     */
    private List<String> missingComponents;
    
    /**
     * Additional metadata about the validation
     */
    private Map<String, Object> metadata;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddressComponents {
        /**
         * Street number and street name
         */
        private String street;
        
        /**
         * Ward (phường/xã)
         */
        private String ward;
        
        /**
         * District (quận/huyện)
         */
        private String district;
        
        /**
         * City/Province (thành phố/tỉnh)
         */
        private String city;
        
        /**
         * Additional notes or apartment/building info
         */
        private String additionalInfo;
    }
}
