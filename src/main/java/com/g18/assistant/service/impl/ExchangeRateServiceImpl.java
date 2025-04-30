package com.g18.assistant.service.impl;

import com.g18.assistant.service.ExchangeRateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private final RestTemplate restTemplate;
    private final String binanceApiUrl = "https://api.binance.com/api/v3/ticker/price?symbol=BNBUSDT";
    
    @Value("${app.exchange-rate.usd-to-vnd}")
    private BigDecimal usdToVndRate;
    
    // Cache the BNB price for 5 minutes to avoid too many API calls
    private BigDecimal cachedBnbPrice;
    private long lastFetchTime = 0;
    private static final long CACHE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    
    public ExchangeRateServiceImpl() {
        this.restTemplate = new RestTemplate();
        // Default USD to VND rate if not configured
        this.usdToVndRate = new BigDecimal("26000");
    }
    
    @Override
    public BigDecimal getBnbUsdPrice() {
        long currentTime = System.currentTimeMillis();
        
        // Return cached price if still valid
        if (cachedBnbPrice != null && (currentTime - lastFetchTime) < CACHE_DURATION_MS) {
            return cachedBnbPrice;
        }
        
        try {
            // Fetch current price from Binance API
            Map<String, Object> response = restTemplate.getForObject(binanceApiUrl, Map.class);
            
            if (response != null && response.containsKey("price")) {
                cachedBnbPrice = new BigDecimal(response.get("price").toString());
                lastFetchTime = currentTime;
                log.info("Updated BNB price: {} USD", cachedBnbPrice);
                return cachedBnbPrice;
            } else {
                log.error("Failed to parse Binance API response");
                return fallbackBnbPrice();
            }
        } catch (Exception e) {
            log.error("Error fetching BNB price from Binance API", e);
            return fallbackBnbPrice();
        }
    }
    
    private BigDecimal fallbackBnbPrice() {
        // Return last cached price if available, or a fallback value
        return cachedBnbPrice != null ? cachedBnbPrice : new BigDecimal("600"); // Fallback price
    }
    
    @Override
    public BigDecimal convertBnbToVnd(BigDecimal bnbAmount) {
        if (bnbAmount == null || bnbAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        
        // Get current BNB price in USD
        BigDecimal bnbUsdPrice = getBnbUsdPrice();
        
        // Calculate VND value: BNB amount * BNB/USD price * USD/VND rate
        return bnbAmount
                .multiply(bnbUsdPrice)
                .multiply(usdToVndRate)
                .setScale(0, RoundingMode.HALF_UP); // Round to whole VND
    }
} 