package com.g18.assistant.service.impl;

import com.g18.assistant.config.VNPayConfig;
import com.g18.assistant.dto.request.VNPayCreateRequest;
import com.g18.assistant.dto.response.VNPayResponse;
import com.g18.assistant.entity.User;
import com.g18.assistant.entity.VNPayTransaction;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.repository.VNPayTransactionRepository;
import com.g18.assistant.service.VNPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@Slf4j
@RequiredArgsConstructor
public class VNPayServiceImpl implements VNPayService {

    private final VNPayConfig vnPayConfig;
    private final VNPayTransactionRepository vnPayTransactionRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public VNPayResponse createPayment(Long userId, VNPayCreateRequest request) {
        log.info("Creating VNPay payment for user: {}, amount: {}", userId, request.getAmount());
        
        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Generate order ID (format: yyyyMMddHHmmss + userId)
        String orderId = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + userId;
        
        // Create transaction record
        VNPayTransaction transaction = VNPayTransaction.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(request.getAmount())
                .status(VNPayTransaction.TransactionStatus.PENDING)
                .transactionId("PENDING_" + orderId) // Temporary ID until payment completes
                .build();
        
        vnPayTransactionRepository.save(transaction);
        
        // Build payment URL
        String paymentUrl = buildPaymentUrl(transaction, request);
        
        return VNPayResponse.fromEntityWithPaymentUrl(transaction, paymentUrl);
    }
    
    private String buildPaymentUrl(VNPayTransaction transaction, VNPayCreateRequest request) {
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.getVersion());
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnPayConfig.getTmnCode());
        vnpParams.put("vnp_Amount", String.valueOf(transaction.getAmount().multiply(new BigDecimal("100")).intValue())); // Amount in VND, multiply by 100
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", transaction.getOrderId());
        vnpParams.put("vnp_OrderInfo", request.getDescription());
        vnpParams.put("vnp_OrderType", "topup"); // Order type for account top-up
        vnpParams.put("vnp_Locale", request.getLanguage() != null ? request.getLanguage() : "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.getReturnUrl());
        
        if (request.getBankCode() != null && !request.getBankCode().isEmpty()) {
            vnpParams.put("vnp_BankCode", request.getBankCode());
        }
        
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(vnPayConfig.getTimezone()));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone(vnPayConfig.getTimezone()));
        
        vnpParams.put("vnp_CreateDate", sdf.format(calendar.getTime()));
        vnpParams.put("vnp_IpAddr", "127.0.0.1"); // Should be replaced with actual IP in controller
        
        // Sort parameters by key
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        
        // Build hash data and query
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        
        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                // Build hash data
                hashData.append(fieldName).append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                
                // Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.UTF_8));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                
                if (fieldNames.indexOf(fieldName) < fieldNames.size() - 1) {
                    hashData.append('&');
                    query.append('&');
                }
            }
        }
        
        // Create secure hash
        String secureHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        query.append("&vnp_SecureHash=").append(secureHash);
        
        return vnPayConfig.getPaymentUrl() + "?" + query.toString();
    }

    @Override
    @Transactional
    public VNPayTransaction processPaymentReturn(Map<String, String> params) {
        log.info("Processing VNPay callback with params: {}", params);
        
        String orderId = params.get("vnp_TxnRef");
        String transactionId = params.get("vnp_TransactionNo");
        String responseCode = params.get("vnp_ResponseCode");
        String secureHash = params.get("vnp_SecureHash");
        
        // Kiểm tra các tham số bắt buộc
        if (orderId == null || orderId.isEmpty()) {
            throw new IllegalArgumentException("Thiếu tham số bắt buộc: vnp_TxnRef (orderId)");
        }
        
        // Kiểm tra xem đã có giao dịch với orderId này chưa
        Optional<VNPayTransaction> existingTransaction = vnPayTransactionRepository.findByOrderId(orderId);
        if (existingTransaction.isPresent()) {
            // Nếu đã có giao dịch và đã được xử lý, trả về giao dịch đó
            VNPayTransaction transaction = existingTransaction.get();
            
            // Nếu giao dịch đã hoàn thành hoặc đã thất bại, không xử lý lại
            if (transaction.getStatus() == VNPayTransaction.TransactionStatus.COMPLETED || 
                transaction.getStatus() == VNPayTransaction.TransactionStatus.FAILED) {
                log.info("Transaction already processed: {}, status: {}", orderId, transaction.getStatus());
                return transaction;
            }
            
            // Nếu giao dịch chưa được xử lý hoàn tất, tiếp tục xử lý
            log.info("Found existing transaction in PENDING status, updating: {}", orderId);
        } else {
            // Không tìm thấy giao dịch, có thể là lỗi
            throw new IllegalArgumentException("Không tìm thấy giao dịch với orderId: " + orderId);
        }
        
        VNPayTransaction transaction = existingTransaction.get();
        
        // Kiểm tra SecureHash
        if (secureHash == null || secureHash.isEmpty()) {
            log.error("Missing SecureHash for transaction {}", orderId);
            transaction.setStatus(VNPayTransaction.TransactionStatus.FAILED);
            return vnPayTransactionRepository.save(transaction);
        }
        
        // Xác thực SecureHash
        if (!validateSecureHash(params, secureHash)) {
            log.error("Invalid secure hash for transaction {}", orderId);
            transaction.setStatus(VNPayTransaction.TransactionStatus.FAILED);
            return vnPayTransactionRepository.save(transaction);
        }
        
        // Đảm bảo transactionId không rỗng
        if (transactionId == null || transactionId.isEmpty()) {
            // Tạo giá trị mặc định nếu không có
            transactionId = "UNKNOWN_" + System.currentTimeMillis();
            log.warn("Missing transactionId, using generated value: {}", transactionId);
        }
        
        // Cập nhật thông tin giao dịch
        transaction.setTransactionId(transactionId);
        transaction.setResponseCode(responseCode);
        transaction.setBankCode(params.get("vnp_BankCode"));
        transaction.setCardType(params.get("vnp_CardType"));
        transaction.setPayDate(params.get("vnp_PayDate"));
        transaction.setSecureHash(secureHash);
        
        // Kiểm tra mã phản hồi (00 = thành công)
        if ("00".equals(responseCode)) {
            transaction.setStatus(VNPayTransaction.TransactionStatus.COMPLETED);
            
            // Cập nhật số dư người dùng nếu chưa cập nhật
            if (!transaction.isBalanceUpdated()) {
                updateUserBalance(transaction.getUserId(), transaction.getAmount());
                transaction.setBalanceUpdated(true);
                log.info("Updated balance for user {} with amount {}", transaction.getUserId(), transaction.getAmount());
            } else {
                log.info("Balance already updated, skipping");
            }
        } else {
            transaction.setStatus(VNPayTransaction.TransactionStatus.FAILED);
            log.warn("Transaction failed with response code: {}", responseCode);
        }
        
        return vnPayTransactionRepository.save(transaction);
    }
    
    @Transactional
    private void updateUserBalance(Long userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Convert BigDecimal to Double for user balance
        Double amountDouble = amount.doubleValue();
        user.setBalance(user.getBalance() + amountDouble);
        
        userRepository.save(user);
        log.info("Updated balance for user {}: added {} VND", userId, amount);
    }
    
    private boolean validateSecureHash(Map<String, String> params, String secureHash) {
        // Create a new map excluding the hash
        Map<String, String> validParams = new HashMap<>();
        params.forEach((key, value) -> {
            if (!key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")) {
                validParams.put(key, value);
            }
        });
        
        // Sort parameters by key
        List<String> fieldNames = new ArrayList<>(validParams.keySet());
        Collections.sort(fieldNames);
        
        // Build hash data
        StringBuilder hashData = new StringBuilder();
        
        for (String fieldName : fieldNames) {
            String fieldValue = validParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName).append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8));
                
                if (fieldNames.indexOf(fieldName) < fieldNames.size() - 1) {
                    hashData.append('&');
                }
            }
        }
        
        // Calculate new hash
        String calculatedHash = hmacSHA512(vnPayConfig.getHashSecret(), hashData.toString());
        
        // Compare the hashes
        return calculatedHash.equals(secureHash);
    }
    
    private String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(), "HmacSHA512");
            hmac.init(secretKeySpec);
            byte[] hmacBytes = hmac.doFinal(data.getBytes());
            return bytesToHex(hmacBytes);
        } catch (Exception e) {
            log.error("Error generating HMAC-SHA512", e);
            throw new RuntimeException("Error generating HMAC-SHA512", e);
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Override
    public VNPayResponse getTransaction(Long id) {
        VNPayTransaction transaction = vnPayTransactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        return VNPayResponse.fromEntity(transaction);
    }

    @Override
    public VNPayResponse getTransactionByOrderId(String orderId) {
        VNPayTransaction transaction = vnPayTransactionRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        return VNPayResponse.fromEntity(transaction);
    }

    @Override
    public List<VNPayResponse> getUserTransactions(Long userId) {
        List<VNPayTransaction> transactions = vnPayTransactionRepository.findByUserId(userId);
        return transactions.stream()
                .map(VNPayResponse::fromEntity)
                .collect(Collectors.toList());
    }
} 