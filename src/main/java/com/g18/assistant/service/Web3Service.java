package com.g18.assistant.service;

import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.entity.TemporaryWallet;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface Web3Service {

    /**
     * Creates a temporary wallet for a user expecting a specific payment amount
     * @param userId User ID to associate with wallet
     * @param expectedAmount Expected payment amount in BNB
     * @return Created wallet entity
     */
    TemporaryWallet createTemporaryWallet(Long userId, BigDecimal expectedAmount);

    /**
     * Gets the BNB balance of a wallet address
     * @param address Wallet address
     * @return Balance in BNB
     */
    BigDecimal getBalance(String address);

    /**
     * Checks transaction status by hash
     * @param txHash Transaction hash
     * @return Transaction entity if found
     */
    BlockchainTransaction checkTransaction(String txHash);

    /**
     * Sends BNB from a wallet to a destination address
     * @param fromPrivateKey Private key of the sending wallet
     * @param toAddress Destination address
     * @param amount Amount of BNB to send
     * @return CompletableFuture for the transaction receipt
     */
    CompletableFuture<TransactionReceipt> sendBnb(String fromPrivateKey, String toAddress, BigDecimal amount);

    /**
     * Sweeps funds from a temporary wallet to the main wallet
     * @param temporaryWallet Wallet to sweep
     * @return Transaction entity for the sweep
     */
    BlockchainTransaction sweepFunds(TemporaryWallet temporaryWallet);

    /**
     * Starts transaction monitoring process
     */
    void startTransactionMonitoring();

    /**
     * Stops transaction monitoring process
     */
    void stopTransactionMonitoring();

    /**
     * Processes pending sweeps for paid wallets
     */
    void processPendingSweeps();

    /**
     * Gets unprocessed transactions for a wallet address
     * @param walletAddress Wallet address
     * @return List of unprocessed transactions
     */
    List<BlockchainTransaction> getUnprocessedTransactionsForWallet(String walletAddress);

    /**
     * Checks if a customer has made a payment to a wallet
     * @param walletAddress Wallet address to check
     * @param expectedAmount Expected payment amount
     * @return true if payment is confirmed, false otherwise
     */
    boolean checkCustomerPayment(String walletAddress, BigDecimal expectedAmount);
    
    /**
     * Updates a user's balance with the specified BNB amount
     * @param userId User ID to update
     * @param bnbAmount Amount in BNB to add to the balance (will be converted to VND)
     */
    void updateUserBalance(Long userId, BigDecimal bnbAmount);
} 