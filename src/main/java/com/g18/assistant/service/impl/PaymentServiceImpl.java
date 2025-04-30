package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.CreatePaymentRequest;
import com.g18.assistant.dto.response.PaymentResponse;
import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.entity.TemporaryWallet;
import com.g18.assistant.entity.User;
import com.g18.assistant.repository.BlockchainTransactionRepository;
import com.g18.assistant.repository.TemporaryWalletRepository;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.service.ExchangeRateService;
import com.g18.assistant.service.PaymentService;
import com.g18.assistant.service.Web3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TemporaryWalletRepository temporaryWalletRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final UserRepository userRepository;
    private final Web3Service web3Service;
    private final ExchangeRateService exchangeRateService;

    @PostConstruct
    @Override
    public void initialize() {
        log.info("Initializing payment service");
        web3Service.startTransactionMonitoring();
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        log.info("Creating payment for user: {}, amount: {}", request.getUserId(), request.getAmount());
        
        // Create a temporary wallet for this payment
        TemporaryWallet wallet = web3Service.createTemporaryWallet(
                request.getUserId(), 
                request.getAmount()
        );
        
        // Set expiry time (24 hours from now)
        wallet.setExpiresAt(LocalDateTime.now().plusHours(24));
        temporaryWalletRepository.save(wallet);
        
        // Convert BNB to VND
        BigDecimal bnbPriceUsd = exchangeRateService.getBnbUsdPrice();
        BigDecimal amountVnd = exchangeRateService.convertBnbToVnd(request.getAmount());
        
        return PaymentResponse.fromWallet(wallet, amountVnd, bnbPriceUsd);
    }

    @Override
    public PaymentResponse getPaymentStatus(Long id) {
        TemporaryWallet wallet = temporaryWalletRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        
        // Only check payment if wallet is still in PENDING status
        if (wallet.getStatus() == TemporaryWallet.WalletStatus.PENDING) {
            // Explicitly check if customer has made payment
            boolean isPaid = web3Service.checkCustomerPayment(
                    wallet.getWalletAddress(), 
                    wallet.getExpectedAmount()
            );
            
            if (isPaid) {
                log.info("Payment confirmed for wallet: {}", wallet.getWalletAddress());
                wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                temporaryWalletRepository.save(wallet);
                
                // Số dư người dùng đã được cập nhật trong Web3ServiceImpl
            } else {
                log.info("Payment not yet received for wallet: {}", wallet.getWalletAddress());
            }
        }
        
        // Convert BNB to VND
        BigDecimal bnbPriceUsd = exchangeRateService.getBnbUsdPrice();
        BigDecimal amountVnd = exchangeRateService.convertBnbToVnd(wallet.getExpectedAmount());
        
        return PaymentResponse.fromWallet(wallet, amountVnd, bnbPriceUsd);
    }

    @Override
    public List<PaymentResponse> getUserPayments(Long userId) {
        List<TemporaryWallet> wallets = temporaryWalletRepository.findByUserId(userId);
        
        // Get current BNB price (once for all wallets)
        BigDecimal bnbPriceUsd = exchangeRateService.getBnbUsdPrice();
        
        // Check pending wallets for payments before returning results
        wallets.forEach(wallet -> {
            if (wallet.getStatus() == TemporaryWallet.WalletStatus.PENDING) {
                boolean isPaid = web3Service.checkCustomerPayment(
                        wallet.getWalletAddress(), 
                        wallet.getExpectedAmount()
                );
                
                if (isPaid) {
                    log.info("Payment confirmed for wallet: {}", wallet.getWalletAddress());
                    wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                    temporaryWalletRepository.save(wallet);
                    
                    // Số dư người dùng đã được cập nhật trong Web3ServiceImpl
                }
            }
        });
        
        return wallets.stream()
                .map(wallet -> {
                    BigDecimal amountVnd = exchangeRateService.convertBnbToVnd(wallet.getExpectedAmount());
                    return PaymentResponse.fromWallet(wallet, amountVnd, bnbPriceUsd);
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<BlockchainTransaction> getPaymentTransactions(Long paymentId) {
        // Find the wallet
        TemporaryWallet wallet = temporaryWalletRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        
        // Return transactions for this wallet
        return blockchainTransactionRepository.findByWalletId(wallet.getId());
    }

    @Override
    @Scheduled(fixedRate = 3600000) // Run every hour
    @Transactional
    public void cleanupExpiredWallets() {
        log.info("Cleaning up expired wallets");
        
        // Find wallets that have expired and are still in PENDING status
        List<TemporaryWallet> expiredWallets = temporaryWalletRepository
                .findByExpiresAtBeforeAndStatus(
                        LocalDateTime.now(), 
                        TemporaryWallet.WalletStatus.PENDING
                );
        
        // Update status to EXPIRED
        for (TemporaryWallet wallet : expiredWallets) {
            wallet.setStatus(TemporaryWallet.WalletStatus.EXPIRED);
            temporaryWalletRepository.save(wallet);
            log.info("Wallet expired: {}", wallet.getWalletAddress());
        }
    }
    
    /**
     * Active check for new payments. This is scheduled to run every 5 minutes to
     * proactively check for new payments in addition to the monitoring thread.
     */
    @Scheduled(fixedRate = 300000) // Run every 5 minutes
    @Transactional
    public void checkPendingPayments() {
        log.info("Checking pending payments");
        
        // Find all pending wallets
        List<TemporaryWallet> pendingWallets = temporaryWalletRepository
                .findByStatus(TemporaryWallet.WalletStatus.PENDING);
        
        // Check each wallet for payment
        for (TemporaryWallet wallet : pendingWallets) {
            try {
                boolean isPaid = web3Service.checkCustomerPayment(
                        wallet.getWalletAddress(), 
                        wallet.getExpectedAmount()
                );
                
                if (isPaid) {
                    log.info("Payment confirmed for wallet: {}", wallet.getWalletAddress());
                    wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                    temporaryWalletRepository.save(wallet);
                    
                    // Số dư người dùng đã được cập nhật trong Web3ServiceImpl
                }
            } catch (Exception e) {
                log.error("Error checking payment for wallet: {}", wallet.getWalletAddress(), e);
            }
        }
    }

    @Override
    public PaymentResponse checkPaymentManually(Long id) {
        log.info("Manually checking payment for ID: {}", id);
        
        TemporaryWallet wallet = temporaryWalletRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        
        // Only perform check if wallet is still in PENDING status
        if (wallet.getStatus() == TemporaryWallet.WalletStatus.PENDING) {
            // Explicitly check if customer has made payment by directly checking blockchain
            boolean isPaid = web3Service.checkCustomerPayment(
                    wallet.getWalletAddress(), 
                    wallet.getExpectedAmount()
            );
            
            if (isPaid) {
                log.info("Payment confirmed for wallet: {}", wallet.getWalletAddress());
                wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                temporaryWalletRepository.save(wallet);
                
                // Số dư người dùng đã được cập nhật trong Web3ServiceImpl
            } else {
                // Check if there are any deposits, even if they don't match the expected amount
                List<BlockchainTransaction> transactions = 
                        web3Service.getUnprocessedTransactionsForWallet(wallet.getWalletAddress());
                
                if (!transactions.isEmpty()) {
                    log.info("Found {} transactions for wallet {}, but amount didn't match expected", 
                            transactions.size(), wallet.getWalletAddress());
                    
                    // Save transactions even if they don't match expected amount
                    for (BlockchainTransaction tx : transactions) {
                        tx.setWallet(wallet);
                        blockchainTransactionRepository.save(tx);
                    }
                } else {
                    log.info("No payment found for wallet: {}", wallet.getWalletAddress());
                }
            }
        }
        
        // Convert BNB to VND
        BigDecimal bnbPriceUsd = exchangeRateService.getBnbUsdPrice();
        BigDecimal amountVnd = exchangeRateService.convertBnbToVnd(wallet.getExpectedAmount());
        
        return PaymentResponse.fromWallet(wallet, amountVnd, bnbPriceUsd);
    }
} 