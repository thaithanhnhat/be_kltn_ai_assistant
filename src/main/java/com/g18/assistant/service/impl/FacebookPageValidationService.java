package com.g18.assistant.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookPageValidationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.facebook.api.url:https://graph.facebook.com/v18.0}")
    private String facebookApiUrl;

    /**
     * Validate if the access token is valid for the specified page
     */
    public boolean validatePageAccessToken(String accessToken, String pageId) {
        try {
            String url = facebookApiUrl + "/" + pageId + "?access_token=" + accessToken + "&fields=id,name,access_token";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            
            JsonNode responseJson = objectMapper.readTree(response);
            
            // Check if the response contains the expected page ID
            if (responseJson.has("id") && pageId.equals(responseJson.get("id").asText())) {
                log.info("Valid access token for page {} ({})", pageId, responseJson.get("name").asText());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Invalid access token for page {}: {}", pageId, e.getMessage());
            return false;
        }
    }

    /**
     * Get page information including name and details
     */
    public Map<String, String> getPageInfo(String accessToken, String pageId) {
        try {
            String url = facebookApiUrl + "/" + pageId + "?access_token=" + accessToken + 
                        "&fields=id,name,category,about,fan_count,verification_status";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            JsonNode responseJson = objectMapper.readTree(response);
            
            Map<String, String> pageInfo = new HashMap<>();
            pageInfo.put("id", responseJson.has("id") ? responseJson.get("id").asText() : "");
            pageInfo.put("name", responseJson.has("name") ? responseJson.get("name").asText() : "");
            pageInfo.put("category", responseJson.has("category") ? responseJson.get("category").asText() : "");
            pageInfo.put("about", responseJson.has("about") ? responseJson.get("about").asText() : "");
            pageInfo.put("fan_count", responseJson.has("fan_count") ? responseJson.get("fan_count").asText() : "0");
            pageInfo.put("verification_status", responseJson.has("verification_status") ? 
                        responseJson.get("verification_status").asText() : "not_verified");
            
            return pageInfo;
            
        } catch (Exception e) {
            log.error("Error getting page info for page {}: {}", pageId, e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Subscribe page to webhook events
     */
    public boolean subscribePageToWebhook(String accessToken, String pageId, String subscribedFields) {
        try {
            String url = facebookApiUrl + "/" + pageId + "/subscribed_apps?access_token=" + accessToken;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> requestBody = new HashMap<>();
            if (subscribedFields != null && !subscribedFields.isEmpty()) {
                requestBody.put("subscribed_fields", subscribedFields);
            } else {
                requestBody.put("subscribed_fields", "messages,messaging_postbacks,messaging_optins,message_deliveries");
            }
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            String response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class).getBody();
            JsonNode responseJson = objectMapper.readTree(response);
            
            boolean success = responseJson.has("success") && responseJson.get("success").asBoolean();
            
            if (success) {
                log.info("Successfully subscribed page {} to webhook", pageId);
            } else {
                log.warn("Failed to subscribe page {} to webhook: {}", pageId, response);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Error subscribing page {} to webhook: {}", pageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Unsubscribe page from webhook events
     */
    public boolean unsubscribePageFromWebhook(String accessToken, String pageId) {
        try {
            String url = facebookApiUrl + "/" + pageId + "/subscribed_apps?access_token=" + accessToken;
            
            String response = restTemplate.exchange(url, HttpMethod.DELETE, null, String.class).getBody();
            JsonNode responseJson = objectMapper.readTree(response);
            
            boolean success = responseJson.has("success") && responseJson.get("success").asBoolean();
            
            if (success) {
                log.info("Successfully unsubscribed page {} from webhook", pageId);
            } else {
                log.warn("Failed to unsubscribe page {} from webhook: {}", pageId, response);
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Error unsubscribing page {} from webhook: {}", pageId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Check if page is subscribed to app
     */
    public boolean isPageSubscribed(String accessToken, String pageId) {
        try {
            String url = facebookApiUrl + "/" + pageId + "/subscribed_apps?access_token=" + accessToken;
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class).getBody();
            JsonNode responseJson = objectMapper.readTree(response);
            
            if (responseJson.has("data") && responseJson.get("data").isArray()) {
                return responseJson.get("data").size() > 0;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking subscription status for page {}: {}", pageId, e.getMessage(), e);
            return false;
        }
    }
}
