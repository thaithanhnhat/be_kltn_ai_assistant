package com.g18.assistant.controller;

import com.g18.assistant.dto.request.CreatePaymentRequest;
import com.g18.assistant.dto.response.PaymentResponse;
import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePaymentRequest request) {
        
        // Set the user ID from the authenticated user
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        request.setUserId(userId);
        
        PaymentResponse payment = paymentService.createPayment(request);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPaymentStatus(@PathVariable Long id) {
        PaymentResponse payment = paymentService.getPaymentStatus(id);
        return ResponseEntity.ok(payment);
    }
    
    @PostMapping("/{id}/check")
    public ResponseEntity<PaymentResponse> forceCheckPayment(@PathVariable Long id) {
        log.info("Manually checking payment status for id: {}", id);
        // This will perform an explicit check of the blockchain for payment
        PaymentResponse payment = paymentService.checkPaymentManually(id);
        return ResponseEntity.ok(payment);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getUserPayments(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getClaim("userId").toString());
        List<PaymentResponse> payments = paymentService.getUserPayments(userId);
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<BlockchainTransaction>> getPaymentTransactions(@PathVariable Long id) {
        List<BlockchainTransaction> transactions = paymentService.getPaymentTransactions(id);
        return ResponseEntity.ok(transactions);
    }
} 