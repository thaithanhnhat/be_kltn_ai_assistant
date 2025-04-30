package com.g18.assistant.service;

import java.math.BigDecimal;

public interface ExchangeRateService {
    
    /**
     * Get the current BNB price in USD from Binance API
     * 
     * @return Current BNB price in USD
     */
    BigDecimal getBnbUsdPrice();
    
    /**
     * Convert BNB amount to VND
     * 
     * @param bnbAmount Amount in BNB
     * @return Equivalent amount in VND
     */
    BigDecimal convertBnbToVnd(BigDecimal bnbAmount);
} 