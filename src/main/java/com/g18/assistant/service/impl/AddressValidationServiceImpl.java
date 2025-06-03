package com.g18.assistant.service.impl;

import com.g18.assistant.dto.response.AddressValidationResponse;
import com.g18.assistant.service.AddressValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AddressValidationServiceImpl implements AddressValidationService {

    // Vietnamese address keywords
    private static final Set<String> STREET_KEYWORDS = Set.of(
        "đường", "phố", "ngõ", "hẻm", "số", "thôn", "ấp", "khu", "tổ"
    );
    
    private static final Set<String> WARD_KEYWORDS = Set.of(
        "phường", "xã", "thị trấn", "tt"
    );
    
    private static final Set<String> DISTRICT_KEYWORDS = Set.of(
        "quận", "huyện", "thành phố", "tp", "thị xã", "tx"
    );
    
    private static final Set<String> CITY_KEYWORDS = Set.of(
        "thành phố", "tp", "tỉnh", "hà nội", "hồ chí minh", "đà nẵng", "hải phòng", "cần thơ"
    );
    
    // Common Vietnamese provinces/cities
    private static final Set<String> VIETNAMESE_PROVINCES = Set.of(
        "hà nội", "hồ chí minh", "đà nẵng", "hải phòng", "cần thơ",
        "an giang", "bà rịa - vũng tàu", "bắc giang", "bắc kạn", "bạc liêu",
        "bắc ninh", "bến tre", "bình định", "bình dương", "bình phước",
        "bình thuận", "cà mau", "cao bằng", "đắk lắk", "đắk nông",
        "điện biên", "đồng nai", "đồng tháp", "gia lai", "hà giang",
        "hà nam", "hà tĩnh", "hải dương", "hậu giang", "hòa bình",
        "hưng yên", "khánh hòa", "kiên giang", "kon tum", "lai châu",
        "lâm đồng", "lạng sơn", "lào cai", "long an", "nam định",
        "nghệ an", "ninh bình", "ninh thuận", "phú thọ", "quảng bình",
        "quảng nam", "quảng ngãi", "quảng ninh", "quảng trị", "sóc trăng",
        "sơn la", "tây ninh", "thái bình", "thái nguyên", "thanh hóa",
        "thừa thiên huế", "tiền giang", "trà vinh", "tuyên quang",
        "vĩnh long", "vĩnh phúc", "yên bái"
    );

    // Regex patterns for address components
    private static final Pattern STREET_NUMBER_PATTERN = Pattern.compile("(?:số\\s+)?(\\d+[a-zA-Z]?)");
    private static final Pattern PHONE_PATTERN = Pattern.compile("\\b(?:\\+84|84|0)\\d{9,10}\\b");

    @Override
    public AddressValidationResponse validateAddress(String address) {
        log.debug("Validating address: {}", address);
        
        if (address == null || address.trim().isEmpty()) {
            return createInvalidResponse(address, "Địa chỉ không được để trống", 
                Arrays.asList("Vui lòng nhập địa chỉ đầy đủ"));
        }

        String normalizedAddress = normalizeAddress(address);
        AddressValidationResponse.AddressComponents components = extractComponents(normalizedAddress);
        
        List<String> validationErrors = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        List<String> missingComponents = new ArrayList<>();
        
        // Validate components
        validateComponents(components, validationErrors, missingComponents, suggestions);
        
        // Check for common issues
        checkCommonIssues(normalizedAddress, validationErrors, suggestions);
        
        boolean isValid = validationErrors.isEmpty() && missingComponents.isEmpty();
        double confidence = calculateConfidence(components, validationErrors.size(), missingComponents.size());
        
        return AddressValidationResponse.builder()
                .isValid(isValid)
                .confidence(confidence)
                .originalAddress(address)
                .standardizedAddress(standardizeAddress(address))
                .components(components)
                .validationErrors(validationErrors)
                .suggestions(suggestions)
                .missingComponents(missingComponents)
                .metadata(createMetadata(normalizedAddress))
                .build();
    }

    @Override
    public boolean isAddressComplete(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        
        AddressValidationResponse.AddressComponents components = extractComponents(normalizeAddress(address));
        
        // An address is complete if it has at least street and city/district
        return components.getStreet() != null && !components.getStreet().isEmpty() &&
               ((components.getCity() != null && !components.getCity().isEmpty()) ||
                (components.getDistrict() != null && !components.getDistrict().isEmpty()));
    }

    @Override
    public String standardizeAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return address;
        }
        
        String normalized = normalizeAddress(address);
        AddressValidationResponse.AddressComponents components = extractComponents(normalized);
        
        StringBuilder standardized = new StringBuilder();
        
        if (components.getStreet() != null && !components.getStreet().isEmpty()) {
            standardized.append(components.getStreet());
        }
        
        if (components.getWard() != null && !components.getWard().isEmpty()) {
            if (standardized.length() > 0) standardized.append(", ");
            standardized.append(components.getWard());
        }
        
        if (components.getDistrict() != null && !components.getDistrict().isEmpty()) {
            if (standardized.length() > 0) standardized.append(", ");
            standardized.append(components.getDistrict());
        }
        
        if (components.getCity() != null && !components.getCity().isEmpty()) {
            if (standardized.length() > 0) standardized.append(", ");
            standardized.append(components.getCity());
        }
        
        return standardized.length() > 0 ? standardized.toString() : normalized;
    }

    @Override
    public AddressValidationResponse extractAddressComponents(String address) {
        if (address == null || address.trim().isEmpty()) {
            return createInvalidResponse(address, "Địa chỉ trống", Arrays.asList("Nhập địa chỉ"));
        }
        
        String normalized = normalizeAddress(address);
        AddressValidationResponse.AddressComponents components = extractComponents(normalized);
        
        return AddressValidationResponse.builder()
                .isValid(true)
                .confidence(0.8)
                .originalAddress(address)
                .standardizedAddress(standardizeAddress(address))
                .components(components)
                .validationErrors(new ArrayList<>())
                .suggestions(new ArrayList<>())
                .missingComponents(new ArrayList<>())
                .build();
    }

    @Override
    public AddressValidationResponse validateAddressFormat(String address) {
        return validateAddress(address);
    }

    @Override
    public boolean containsVietnameseAddressKeywords(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }
        
        String normalized = normalizeAddress(address);
        
        return STREET_KEYWORDS.stream().anyMatch(normalized::contains) ||
               WARD_KEYWORDS.stream().anyMatch(normalized::contains) ||
               DISTRICT_KEYWORDS.stream().anyMatch(normalized::contains) ||
               CITY_KEYWORDS.stream().anyMatch(normalized::contains) ||
               VIETNAMESE_PROVINCES.stream().anyMatch(normalized::contains);
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";
        
        return address.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[,;]+", ",");
    }

    private AddressValidationResponse.AddressComponents extractComponents(String normalizedAddress) {
        AddressValidationResponse.AddressComponents.AddressComponentsBuilder builder = 
            AddressValidationResponse.AddressComponents.builder();
        
        // Extract city/province
        String city = extractCity(normalizedAddress);
        builder.city(city);
        
        // Extract district
        String district = extractDistrict(normalizedAddress);
        builder.district(district);
        
        // Extract ward
        String ward = extractWard(normalizedAddress);
        builder.ward(ward);
        
        // Extract street (remaining part)
        String street = extractStreet(normalizedAddress, city, district, ward);
        builder.street(street);
        
        return builder.build();
    }

    private String extractCity(String address) {
        for (String province : VIETNAMESE_PROVINCES) {
            if (address.contains(province)) {
                return capitalizeFirstLetter(province);
            }
        }
        
        // Look for city keywords
        for (String keyword : CITY_KEYWORDS) {
            int index = address.indexOf(keyword);
            if (index >= 0) {
                String part = extractAfterKeyword(address, keyword, index);
                if (part != null && !part.isEmpty()) {
                    return capitalizeFirstLetter(keyword + " " + part);
                }
            }
        }
        
        return null;
    }

    private String extractDistrict(String address) {
        for (String keyword : DISTRICT_KEYWORDS) {
            int index = address.indexOf(keyword);
            if (index >= 0) {
                String part = extractAfterKeyword(address, keyword, index);
                if (part != null && !part.isEmpty()) {
                    return capitalizeFirstLetter(keyword + " " + part);
                }
            }
        }
        return null;
    }

    private String extractWard(String address) {
        for (String keyword : WARD_KEYWORDS) {
            int index = address.indexOf(keyword);
            if (index >= 0) {
                String part = extractAfterKeyword(address, keyword, index);
                if (part != null && !part.isEmpty()) {
                    return capitalizeFirstLetter(keyword + " " + part);
                }
            }
        }
        return null;
    }

    private String extractStreet(String address, String city, String district, String ward) {
        String remaining = address;
        
        // Remove extracted components
        if (city != null) remaining = remaining.replace(city.toLowerCase(), "");
        if (district != null) remaining = remaining.replace(district.toLowerCase(), "");
        if (ward != null) remaining = remaining.replace(ward.toLowerCase(), "");
        
        // Clean up
        remaining = remaining.replaceAll("[,;]+", " ")
                           .replaceAll("\\s+", " ")
                           .trim();
        
        if (remaining.isEmpty()) {
            return null;
        }
        
        return capitalizeFirstLetter(remaining);
    }

    private String extractAfterKeyword(String address, String keyword, int index) {
        String after = address.substring(index + keyword.length()).trim();
        
        // Extract until next comma or district/ward keyword
        String[] parts = after.split("[,;]");
        if (parts.length > 0) {
            String part = parts[0].trim();
            
            // Stop at next administrative keyword
            for (String stopKeyword : Arrays.asList("quận", "huyện", "phường", "xã", "thành phố", "tỉnh")) {
                int stopIndex = part.indexOf(stopKeyword);
                if (stopIndex > 0) {
                    part = part.substring(0, stopIndex).trim();
                    break;
                }
            }
            
            return part.isEmpty() ? null : part;
        }
        
        return null;
    }

    private void validateComponents(AddressValidationResponse.AddressComponents components,
                                  List<String> validationErrors,
                                  List<String> missingComponents,
                                  List<String> suggestions) {
        
        if (components.getStreet() == null || components.getStreet().trim().isEmpty()) {
            missingComponents.add("street");
            suggestions.add("Thêm tên đường hoặc số nhà");
        }
        
        if (components.getDistrict() == null || components.getDistrict().trim().isEmpty()) {
            if (components.getCity() == null || components.getCity().trim().isEmpty()) {
                missingComponents.add("district_or_city");
                suggestions.add("Thêm quận/huyện hoặc thành phố");
            }
        }
        
        // Validate street format
        if (components.getStreet() != null && components.getStreet().length() < 3) {
            validationErrors.add("Tên đường quá ngắn");
            suggestions.add("Nhập tên đường đầy đủ hơn");
        }
    }

    private void checkCommonIssues(String address, List<String> validationErrors, List<String> suggestions) {
        // Check for phone numbers in address
        Matcher phoneMatcher = PHONE_PATTERN.matcher(address);
        if (phoneMatcher.find()) {
            validationErrors.add("Địa chỉ không nên chứa số điện thoại");
            suggestions.add("Xóa số điện thoại khỏi địa chỉ");
        }
        
        // Check if too short
        if (address.length() < 10) {
            validationErrors.add("Địa chỉ quá ngắn");
            suggestions.add("Nhập địa chỉ chi tiết hơn");
        }
        
        // Check for common typos or missing info
        if (!containsVietnameseAddressKeywords(address)) {
            validationErrors.add("Không tìm thấy từ khóa địa chỉ Việt Nam");
            suggestions.add("Thêm thông tin quận/huyện, phường/xã, thành phố");
        }
    }

    private double calculateConfidence(AddressValidationResponse.AddressComponents components,
                                     int errorCount, int missingCount) {
        double baseConfidence = 1.0;
        
        // Reduce confidence based on missing components
        if (components.getStreet() == null) baseConfidence -= 0.3;
        if (components.getWard() == null) baseConfidence -= 0.1;
        if (components.getDistrict() == null) baseConfidence -= 0.2;
        if (components.getCity() == null) baseConfidence -= 0.2;
        
        // Reduce confidence based on errors
        baseConfidence -= (errorCount * 0.1);
        baseConfidence -= (missingCount * 0.15);
        
        return Math.max(0.0, Math.min(1.0, baseConfidence));
    }

    private Map<String, Object> createMetadata(String normalizedAddress) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("normalized_address", normalizedAddress);
        metadata.put("has_vietnamese_keywords", containsVietnameseAddressKeywords(normalizedAddress));
        metadata.put("length", normalizedAddress.length());
        metadata.put("validation_timestamp", System.currentTimeMillis());
        return metadata;
    }

    private AddressValidationResponse createInvalidResponse(String address, String error, List<String> suggestions) {
        return AddressValidationResponse.builder()
                .isValid(false)
                .confidence(0.0)
                .originalAddress(address)
                .standardizedAddress(address)
                .components(AddressValidationResponse.AddressComponents.builder().build())
                .validationErrors(Arrays.asList(error))
                .suggestions(suggestions)
                .missingComponents(new ArrayList<>())
                .metadata(new HashMap<>())
                .build();
    }

    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}