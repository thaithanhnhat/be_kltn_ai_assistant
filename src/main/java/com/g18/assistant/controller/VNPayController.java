package com.g18.assistant.controller;

import com.g18.assistant.dto.request.VNPayCreateRequest;
import com.g18.assistant.dto.response.VNPayResponse;
import com.g18.assistant.entity.VNPayTransaction;
import com.g18.assistant.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vnpay")
@RequiredArgsConstructor
@Slf4j
public class VNPayController {

    private final VNPayService vnPayService;

    @PostMapping("/create-payment")
    public ResponseEntity<VNPayResponse> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VNPayCreateRequest request,
            HttpServletRequest servletRequest) {
        
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        log.info("Creating VNPay payment for user ID: {}", userId);
        
        VNPayResponse response = vnPayService.createPayment(userId, request);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/return")
    public ResponseEntity<String> paymentReturn(HttpServletRequest request) {
        log.info("Received payment return from VNPay");
        
        Map<String, String> params = getRequestParameters(request);
        log.info("VNPay return params: {}", params);
        
        try {
            VNPayTransaction transaction = vnPayService.processPaymentReturn(params);
            
            // Redirect based on payment status
            String redirectUrl;
            if (transaction.getStatus() == VNPayTransaction.TransactionStatus.COMPLETED) {
                redirectUrl = "/payment/success?orderId=" + transaction.getOrderId();
            } else {
                redirectUrl = "/payment/failure?orderId=" + transaction.getOrderId();
            }
            
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", redirectUrl)
                    .build();
        } catch (Exception e) {
            log.error("Error processing VNPay return", e);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header("Location", "/payment/error")
                    .build();
        }
    }
    
    @PostMapping("/process-callback")
    public ResponseEntity<VNPayResponse> processCallback(@RequestBody Map<String, String> params) {
        log.info("Processing payment callback from frontend with params: {}", params);
        
        try {
            // Kiểm tra tham số bắt buộc
            if (!params.containsKey("vnp_TxnRef") || params.get("vnp_TxnRef") == null || params.get("vnp_TxnRef").isEmpty()) {
                log.error("Missing required parameter: vnp_TxnRef");
                return ResponseEntity.badRequest().body(
                    VNPayResponse.builder()
                        .status("FAILED")
                        .build()
                );
            }
            
            // Xử lý callback và cập nhật số dư người dùng
            VNPayTransaction transaction = vnPayService.processPaymentReturn(params);
            
            // Trả về thông tin giao dịch đã được cập nhật
            return ResponseEntity.ok(VNPayResponse.fromEntity(transaction));
        } catch (Exception e) {
            log.error("Error processing VNPay callback: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(VNPayResponse.builder()
                            .status("FAILED")
                            .build());
        }
    }
    
    @PostMapping("/ipn")
    public ResponseEntity<String> ipnHandler(HttpServletRequest request) {
        log.info("Received IPN notification from VNPay");
        
        Map<String, String> params = getRequestParameters(request);
        log.info("VNPay IPN params: {}", params);
        
        try {
            VNPayTransaction transaction = vnPayService.processPaymentReturn(params);
            
            // Return the appropriate response to VNPay
            if (transaction.getStatus() == VNPayTransaction.TransactionStatus.COMPLETED) {
                return ResponseEntity.ok("{\"RspCode\":\"00\",\"Message\":\"Confirm Success\"}");
            } else {
                return ResponseEntity.ok("{\"RspCode\":\"99\",\"Message\":\"Payment Failed\"}");
            }
        } catch (Exception e) {
            log.error("Error processing VNPay IPN", e);
            return ResponseEntity.ok("{\"RspCode\":\"99\",\"Message\":\"Unknown Error\"}");
        }
    }
    
    @GetMapping("/transactions")
    public ResponseEntity<List<VNPayResponse>> getUserTransactions(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        List<VNPayResponse> transactions = vnPayService.getUserTransactions(userId);
        return ResponseEntity.ok(transactions);
    }
    
    @GetMapping("/transactions/{id}")
    public ResponseEntity<VNPayResponse> getTransaction(@PathVariable Long id) {
        VNPayResponse transaction = vnPayService.getTransaction(id);
        return ResponseEntity.ok(transaction);
    }
    
    @GetMapping("/transactions/order/{orderId}")
    public ResponseEntity<VNPayResponse> getTransactionByOrderId(@PathVariable String orderId) {
        VNPayResponse transaction = vnPayService.getTransactionByOrderId(orderId);
        return ResponseEntity.ok(transaction);
    }
    
    private Map<String, String> getRequestParameters(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> paramNames = request.getParameterNames();
        
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            String paramValue = request.getParameter(paramName);
            params.put(paramName, paramValue);
        }
        
        return params;
    }
} 