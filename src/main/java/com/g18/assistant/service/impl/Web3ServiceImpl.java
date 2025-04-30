package com.g18.assistant.service.impl;

import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.entity.TemporaryWallet;
import com.g18.assistant.entity.User;
import com.g18.assistant.repository.BlockchainTransactionRepository;
import com.g18.assistant.repository.TemporaryWalletRepository;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.service.ExchangeRateService;
import com.g18.assistant.service.Web3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class Web3ServiceImpl implements Web3Service {

    private final TemporaryWalletRepository temporaryWalletRepository;
    private final BlockchainTransactionRepository blockchainTransactionRepository;
    private final UserRepository userRepository;
    private final ExchangeRateService exchangeRateService;
    
    // Cache chỉ cho session hiện tại, tránh update trong một request
    private final ConcurrentHashMap<String, Boolean> processedTransactionsInSession = new ConcurrentHashMap<>();
    
    @Value("${app.bnb.testnet.url}")
    private String bnbTestnetUrl;
    
    @Value("${app.bnb.main-wallet-address}")
    private String mainWalletAddress;
    
    @Value("${app.bnb.gas-limit}")
    private BigInteger gasLimit;
    
    @Value("${app.bnb.gas-price}")
    private BigInteger gasPrice;
    
    private Web3j web3j;
    private ExecutorService executorService;
    private AtomicBoolean monitoringActive = new AtomicBoolean(false);
    
    @PostConstruct
    public void init() {
        // Initialize Web3j with BSC Testnet RPC URL
        web3j = Web3j.build(new HttpService(bnbTestnetUrl));
        executorService = Executors.newFixedThreadPool(5);
        
        try {
            // Verify connection
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            log.info("Connected to BSC Testnet: {}", clientVersion);
        } catch (IOException e) {
            log.error("Failed to connect to BSC Testnet", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        if (web3j != null) {
            web3j.shutdown();
        }
        
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    @Override
    @Transactional
    public TemporaryWallet createTemporaryWallet(Long userId, BigDecimal expectedAmount) {
        try {
            // Create a new keypair
            ECKeyPair keyPair = Keys.createEcKeyPair();
            Credentials credentials = Credentials.create(keyPair);
            
            String privateKey = credentials.getEcKeyPair().getPrivateKey().toString(16);
            String address = credentials.getAddress();
            
            // Create and save the temporary wallet entity
            TemporaryWallet wallet = TemporaryWallet.builder()
                    .walletAddress(address)
                    .privateKey(privateKey)
                    .userId(userId)
                    .expectedAmount(expectedAmount)
                    .status(TemporaryWallet.WalletStatus.PENDING)
                    .swept(false)
                    .build();
            
            return temporaryWalletRepository.save(wallet);
        } catch (InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Failed to create wallet", e);
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    @Override
    public BigDecimal getBalance(String address) {
        try {
            BigInteger wei = web3j.ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
            
            return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        } catch (IOException e) {
            log.error("Failed to get balance for address: {}", address, e);
            throw new RuntimeException("Failed to get balance", e);
        }
    }

    @Override
    public BlockchainTransaction checkTransaction(String txHash) {
        try {
            // Check if we already have this transaction
            Optional<BlockchainTransaction> existingTx = blockchainTransactionRepository.findByTxHash(txHash);
            if (existingTx.isPresent()) {
                return existingTx.get();
            }
            
            // Get transaction receipt
            EthGetTransactionReceipt receiptResponse = web3j.ethGetTransactionReceipt(txHash).send();
            if (!receiptResponse.hasError() && receiptResponse.getTransactionReceipt().isPresent()) {
                TransactionReceipt receipt = receiptResponse.getTransactionReceipt().get();
                
                // Get the block timestamp
                EthBlock.Block block = web3j.ethGetBlockByNumber(
                        DefaultBlockParameterName.LATEST, false).send().getBlock();
                
                LocalDateTime blockTimestamp = LocalDateTime.ofEpochSecond(
                        block.getTimestamp().longValue(), 0, ZoneOffset.UTC);
                
                // Create transaction record
                BlockchainTransaction transaction = new BlockchainTransaction();
                transaction.setTxHash(txHash);
                transaction.setFromAddress(receipt.getFrom());
                transaction.setToAddress(receipt.getTo());
                transaction.setBlockNumber(receipt.getBlockNumber().longValue());
                transaction.setGasUsed(new BigDecimal(receipt.getGasUsed()));
                transaction.setBlockTimestamp(blockTimestamp);
                
                // Get the temporary wallet if this is a payment to one of our wallets
                temporaryWalletRepository.findByWalletAddress(receipt.getTo()).ifPresent(wallet -> {
                    transaction.setWallet(wallet);
                    transaction.setTransactionType(BlockchainTransaction.TransactionType.DEPOSIT);
                    
                    // Update wallet status
                    wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                    temporaryWalletRepository.save(wallet);
                });
                
                // If it's from one of our wallets, it's a sweep transaction
                temporaryWalletRepository.findByWalletAddress(receipt.getFrom()).ifPresent(wallet -> {
                    transaction.setWallet(wallet);
                    transaction.setTransactionType(BlockchainTransaction.TransactionType.SWEEP);
                    
                    // Update wallet swept status if it's a sweep to the main wallet
                    if (receipt.getTo().equalsIgnoreCase(mainWalletAddress)) {
                        wallet.setSwept(true);
                        wallet.setStatus(TemporaryWallet.WalletStatus.SWEPT);
                        temporaryWalletRepository.save(wallet);
                    }
                });
                
                transaction.setStatus(receipt.isStatusOK() 
                        ? BlockchainTransaction.TransactionStatus.CONFIRMED 
                        : BlockchainTransaction.TransactionStatus.FAILED);
                
                // Save and return
                return blockchainTransactionRepository.save(transaction);
            }
            
            return null;
        } catch (IOException e) {
            log.error("Failed to check transaction: {}", txHash, e);
            throw new RuntimeException("Failed to check transaction", e);
        }
    }

    @Override
    @Async
    public CompletableFuture<TransactionReceipt> sendBnb(String fromPrivateKey, String toAddress, BigDecimal amount) {
        CompletableFuture<TransactionReceipt> future = new CompletableFuture<>();
        
        executorService.submit(() -> {
            try {
                // Create credentials from private key
                Credentials credentials = Credentials.create(fromPrivateKey);
                
                // Transfer BNB
                TransactionReceipt receipt = Transfer.sendFunds(
                        web3j,
                        credentials, 
                        toAddress,
                        amount,
                        Convert.Unit.ETHER
                ).send();
                
                future.complete(receipt);
            } catch (Exception e) {
                log.error("Failed to send BNB", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    @Override
    @Transactional
    public BlockchainTransaction sweepFunds(TemporaryWallet temporaryWallet) {
        try {
            // Get wallet balance
            BigDecimal balance = getBalance(temporaryWallet.getWalletAddress());
            
            // Calculate gas cost
            BigDecimal gasCost = Convert.fromWei(
                    new BigDecimal(gasPrice.multiply(gasLimit)), 
                    Convert.Unit.ETHER
            );
            
            // Check if balance is greater than gas cost (to ensure we can pay for the transaction)
            if (balance.compareTo(gasCost) <= 0) {
                log.error("Insufficient balance to cover gas costs for wallet: {}", 
                        temporaryWallet.getWalletAddress());
                return null;
            }
            
            // Calculate amount to send (balance - gas cost)
            BigDecimal amountToSend = balance.subtract(gasCost);
            
            // Send transaction
            TransactionReceipt receipt = sendBnb(
                    temporaryWallet.getPrivateKey(),
                    mainWalletAddress,
                    amountToSend
            ).get(); // Wait for completion since this is a critical operation
            
            // Create and save transaction record
            BlockchainTransaction transaction = BlockchainTransaction.builder()
                    .txHash(receipt.getTransactionHash())
                    .fromAddress(temporaryWallet.getWalletAddress())
                    .toAddress(mainWalletAddress)
                    .amount(amountToSend)
                    .gasUsed(new BigDecimal(receipt.getGasUsed()))
                    .status(receipt.isStatusOK() 
                            ? BlockchainTransaction.TransactionStatus.CONFIRMED 
                            : BlockchainTransaction.TransactionStatus.FAILED)
                    .transactionType(BlockchainTransaction.TransactionType.SWEEP)
                    .wallet(temporaryWallet)
                    .build();
            
            // Update wallet status
            if (receipt.isStatusOK()) {
                temporaryWallet.setSwept(true);
                temporaryWallet.setStatus(TemporaryWallet.WalletStatus.SWEPT);
                temporaryWalletRepository.save(temporaryWallet);
            }
            
            return blockchainTransactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Failed to sweep funds from wallet: {}", temporaryWallet.getWalletAddress(), e);
            throw new RuntimeException("Failed to sweep funds", e);
        }
    }

    @Override
    public void startTransactionMonitoring() {
        if (monitoringActive.compareAndSet(false, true)) {
            log.info("Starting transaction monitoring");
            
            // Run monitoring in a separate thread
            executorService.submit(this::monitorTransactions);
        }
    }

    @Override
    public void stopTransactionMonitoring() {
        monitoringActive.set(false);
        log.info("Transaction monitoring stopped");
    }

    private void monitorTransactions() {
        while (monitoringActive.get()) {
            try {
                // Get all active temporary wallets
                List<TemporaryWallet> activeWallets = temporaryWalletRepository.findByStatus(
                        TemporaryWallet.WalletStatus.PENDING);
                
                for (TemporaryWallet wallet : activeWallets) {
                    try {
                        // Check current balance
                        BigDecimal currentBalance = getBalance(wallet.getWalletAddress());
                        BigDecimal expectedAmount = wallet.getExpectedAmount();
                        
                        // If balance meets or exceeds expected amount, check for transactions
                        if (currentBalance.compareTo(BigDecimal.ZERO) > 0) {
                            log.info("Detected balance {} BNB for wallet: {}", 
                                    currentBalance, wallet.getWalletAddress());
                                    
                            // Check for new transactions
                            List<BlockchainTransaction> newTransactions = 
                                    getUnprocessedTransactionsForWallet(wallet.getWalletAddress());
                            
                            // Process each transaction
                            for (BlockchainTransaction transaction : newTransactions) {
                                // Check if amount matches or is greater than expected
                                if (transaction.getAmount() != null && 
                                    (expectedAmount == null || transaction.getAmount().compareTo(expectedAmount) >= 0)) {
                                    
                                    // Update wallet status to PAID
                                    wallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                                    temporaryWalletRepository.save(wallet);
                                    
                                    // Save transaction with CONFIRMED status
                                    transaction.setStatus(BlockchainTransaction.TransactionStatus.CONFIRMED);
                                    blockchainTransactionRepository.save(transaction);
                                    
                                    log.info("Payment confirmed for wallet: {}, transaction hash: {}, amount: {}", 
                                            wallet.getWalletAddress(), transaction.getTxHash(), transaction.getAmount());
                                    
                                    // Update user balance directly here
                                    updateUserBalanceForTransaction(wallet.getUserId(), transaction.getAmount(), transaction);
                                } else {
                                    log.warn("Payment amount mismatch for wallet: {}. Expected: {}, Received: {}", 
                                            wallet.getWalletAddress(), expectedAmount, transaction.getAmount());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error processing wallet: {}", wallet.getWalletAddress(), e);
                    }
                }
                
                // Sleep for a short interval to avoid overloading the node
                Thread.sleep(15000); // 15 seconds
            } catch (InterruptedException e) {
                log.error("Transaction monitoring interrupted", e);
                break;
            } catch (Exception e) {
                log.error("Error in transaction monitoring", e);
                // Continue monitoring despite errors
            }
        }
    }

    @Override
    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void processPendingSweeps() {
        log.info("Processing pending sweeps");
        
        // Get all paid wallets that haven't been swept yet
        List<TemporaryWallet> walletsToSweep = temporaryWalletRepository.findByStatusAndSweptFalse(
                TemporaryWallet.WalletStatus.PAID);
        
        for (TemporaryWallet wallet : walletsToSweep) {
            try {
                // Check if user balance has been updated for this wallet
                List<BlockchainTransaction> depositTransactions = blockchainTransactionRepository.findByWalletAndTransactionType(
                        wallet, BlockchainTransaction.TransactionType.DEPOSIT);
                
                // Update user balance for any deposit transaction that hasn't been processed yet
                for (BlockchainTransaction tx : depositTransactions) {
                    if (tx.getStatus() == BlockchainTransaction.TransactionStatus.CONFIRMED && 
                            tx.getAmount() != null && tx.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                        // Update user balance to ensure it was processed (với cơ chế bảo vệ)
                        updateUserBalanceForTransaction(wallet.getUserId(), tx.getAmount(), tx);
                    }
                }
                
                // Sweep funds to main wallet
                BlockchainTransaction transaction = sweepFunds(wallet);
                
                if (transaction != null) {
                    log.info("Swept funds from wallet: {} to main wallet. TX Hash: {}", 
                            wallet.getWalletAddress(), transaction.getTxHash());
                }
            } catch (Exception e) {
                log.error("Failed to sweep funds from wallet: {}", wallet.getWalletAddress(), e);
            }
        }
    }

    @Override
    public List<BlockchainTransaction> getUnprocessedTransactionsForWallet(String walletAddress) {
        List<BlockchainTransaction> result = new ArrayList<>();
        
        try {
            // Get the last 10 blocks to increase chance of detecting transactions
            long latestBlockNumber = web3j.ethBlockNumber().send().getBlockNumber().longValue();
            
            // Check last 10 blocks (or fewer if we're near the genesis block)
            for (long i = 0; i < 10 && latestBlockNumber - i >= 0; i++) {
                EthBlock ethBlock = web3j.ethGetBlockByNumber(
                        org.web3j.protocol.core.DefaultBlockParameter.valueOf(BigInteger.valueOf(latestBlockNumber - i)), 
                        true).send();
                
                // Iterate through transactions in the block
                ethBlock.getBlock().getTransactions().forEach(tx -> {
                    EthBlock.TransactionObject transaction = (EthBlock.TransactionObject) tx;
                    
                    // Check if this transaction is to our wallet
                    if (transaction.getTo() != null && 
                        transaction.getTo().equalsIgnoreCase(walletAddress)) {
                        
                        // Check if we've already processed this transaction
                        Optional<BlockchainTransaction> existingTx = 
                                blockchainTransactionRepository.findByTxHash(transaction.getHash());
                        
                        if (existingTx.isEmpty()) {
                            try {
                                // Get transaction receipt
                                TransactionReceipt receipt = web3j.ethGetTransactionReceipt(transaction.getHash())
                                        .send().getTransactionReceipt().orElse(null);
                                
                                if (receipt != null && receipt.isStatusOK()) {
                                    // Get the block timestamp
                                    LocalDateTime blockTimestamp = LocalDateTime.ofEpochSecond(
                                            ethBlock.getBlock().getTimestamp().longValue(), 
                                            0, ZoneOffset.UTC);
                                    
                                    // Create a new transaction record
                                    BlockchainTransaction blockchainTx = BlockchainTransaction.builder()
                                            .txHash(transaction.getHash())
                                            .fromAddress(transaction.getFrom())
                                            .toAddress(transaction.getTo())
                                            .amount(Convert.fromWei(
                                                    new BigDecimal(transaction.getValue()), 
                                                    Convert.Unit.ETHER))
                                            .blockNumber(transaction.getBlockNumber().longValue())
                                            .blockTimestamp(blockTimestamp)
                                            .status(BlockchainTransaction.TransactionStatus.CONFIRMED)
                                            .transactionType(BlockchainTransaction.TransactionType.DEPOSIT)
                                            .build();
                                    
                                    // Look up the wallet
                                    Optional<TemporaryWallet> wallet = temporaryWalletRepository.findByWalletAddress(walletAddress);
                                    wallet.ifPresent(blockchainTx::setWallet);
                                    
                                    result.add(blockchainTx);
                                    
                                    log.info("Found new transaction for wallet {}: tx hash {}, amount {}", 
                                            walletAddress, transaction.getHash(), 
                                            Convert.fromWei(new BigDecimal(transaction.getValue()), Convert.Unit.ETHER));
                                }
                            } catch (IOException e) {
                                log.error("Error checking transaction receipt: {}", transaction.getHash(), e);
                            }
                        }
                    }
                });
            }
        } catch (IOException e) {
            log.error("Error fetching blocks for wallet: {}", walletAddress, e);
        }
        
        return result;
    }

    @Override
    public boolean checkCustomerPayment(String walletAddress, BigDecimal expectedAmount) {
        try {
            // First check the wallet balance
            BigDecimal currentBalance = getBalance(walletAddress);
            
            // If balance is greater than or equal to expected amount, payment is confirmed
            if (currentBalance.compareTo(expectedAmount) >= 0) {
                log.info("Wallet {} has sufficient balance: {}", walletAddress, currentBalance);
                
                // Get transactions for the wallet to record them
                List<BlockchainTransaction> transactions = getUnprocessedTransactionsForWallet(walletAddress);
                
                // If we found transactions, process them
                if (!transactions.isEmpty()) {
                    for (BlockchainTransaction tx : transactions) {
                        tx.setStatus(BlockchainTransaction.TransactionStatus.CONFIRMED);
                        blockchainTransactionRepository.save(tx);
                    }
                    
                    // Update wallet status if it's one of our temporary wallets
                    Optional<TemporaryWallet> walletOpt = temporaryWalletRepository.findByWalletAddress(walletAddress);
                    if (walletOpt.isPresent()) {
                        TemporaryWallet tempWallet = walletOpt.get();
                        tempWallet.setStatus(TemporaryWallet.WalletStatus.PAID);
                        temporaryWalletRepository.save(tempWallet);
                        
                        // Cập nhật số dư người dùng với cơ chế bảo vệ
                        if (!transactions.isEmpty()) {
                            BlockchainTransaction firstTx = transactions.get(0);
                            updateUserBalanceForTransaction(tempWallet.getUserId(), expectedAmount, firstTx);
                        } else {
                            // Trường hợp không có giao dịch nào được tìm thấy, 
                            // tạo một giao dịch ảo để lưu trạng thái
                            BlockchainTransaction manualTx = createManualTransaction(
                                    walletAddress, expectedAmount, tempWallet);
                            updateUserBalanceForTransaction(tempWallet.getUserId(), expectedAmount, manualTx);
                        }
                    }
                }
                
                return true;
            } else {
                log.info("Insufficient balance in wallet {}: current balance={}, expected={}", 
                        walletAddress, currentBalance, expectedAmount);
                return false;
            }
        } catch (Exception e) {
            log.error("Error checking customer payment for wallet: {}", walletAddress, e);
            return false;
        }
    }
    
    /**
     * Tạo một giao dịch thủ công để theo dõi việc cập nhật số dư
     */
    private BlockchainTransaction createManualTransaction(String walletAddress, BigDecimal amount, 
                                                        TemporaryWallet wallet) {
        String manualTxHash = "manual-" + System.currentTimeMillis();
        
        BlockchainTransaction tx = BlockchainTransaction.builder()
                .txHash(manualTxHash)
                .fromAddress("manual-address")
                .toAddress(walletAddress)
                .amount(amount)
                .status(BlockchainTransaction.TransactionStatus.CONFIRMED)
                .transactionType(BlockchainTransaction.TransactionType.DEPOSIT)
                .wallet(wallet)
                .balanceUpdated(false)
                .build();
        
        return blockchainTransactionRepository.save(tx);
    }

    /**
     * Update user balance for a specific transaction with duplicate protection
     * using database persistence
     * 
     * @param userId User ID
     * @param bnbAmount Amount in BNB
     * @param transaction The transaction object
     */
    @Transactional
    private void updateUserBalanceForTransaction(Long userId, BigDecimal bnbAmount, BlockchainTransaction transaction) {
        // Kiểm tra cache tạm thời trước (để tránh update trong 1 request)
        String txHash = transaction.getTxHash();
        if (processedTransactionsInSession.containsKey(txHash)) {
            log.info("Transaction already processed in this session: {}", txHash);
            return;
        }
        
        // Kiểm tra xem giao dịch này đã được xử lý số dư chưa
        if (transaction.isBalanceUpdated()) {
            log.info("Transaction already processed for balance update: {}", txHash);
            return;
        }
        
        try {
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                
                // Convert BNB to VND
                BigDecimal vndAmount = exchangeRateService.convertBnbToVnd(bnbAmount);
                
                // Update user balance (convert BigDecimal to Double)
                double currentBalance = user.getBalance();
                double newBalance = currentBalance + vndAmount.doubleValue();
                user.setBalance(newBalance);
                
                // Save updated user
                userRepository.save(user);
                
                // Đánh dấu giao dịch đã được xử lý (lưu vào DB)
                transaction.setBalanceUpdated(true);
                blockchainTransactionRepository.save(transaction);
                
                // Cập nhật cache tạm thời
                processedTransactionsInSession.put(txHash, true);
                
                log.info("Updated balance for user {}: added {} VND, new balance: {} VND, txHash: {}", 
                        userId, vndAmount.doubleValue(), newBalance, txHash);
            } else {
                log.error("Could not update balance - user not found: {}", userId);
            }
        } catch (Exception e) {
            log.error("Error updating user balance for transaction {}: {}", txHash, e.getMessage());
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    @Override
    @Transactional
    public void updateUserBalance(Long userId, BigDecimal bnbAmount) {
        try {
            // Tạo một giao dịch thủ công để theo dõi việc cập nhật số dư
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                BlockchainTransaction manualTx = createManualTransaction(
                        "manual-address-" + userId, 
                        bnbAmount, 
                        null);  // Không có ví cụ thể
                
                updateUserBalanceForTransaction(userId, bnbAmount, manualTx);
            }
        } catch (Exception e) {
            log.error("Error in manual balance update: {}", e.getMessage());
        }
    }
} 