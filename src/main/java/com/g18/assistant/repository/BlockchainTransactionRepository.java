package com.g18.assistant.repository;

import com.g18.assistant.entity.BlockchainTransaction;
import com.g18.assistant.entity.TemporaryWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, Long> {

    Optional<BlockchainTransaction> findByTxHash(String txHash);
    
    List<BlockchainTransaction> findByToAddress(String toAddress);
    
    List<BlockchainTransaction> findByFromAddress(String fromAddress);
    
    List<BlockchainTransaction> findByWalletId(Long walletId);
    
    List<BlockchainTransaction> findByStatus(BlockchainTransaction.TransactionStatus status);
    
    List<BlockchainTransaction> findByTransactionType(BlockchainTransaction.TransactionType type);
    
    List<BlockchainTransaction> findByWalletAndTransactionType(TemporaryWallet wallet, BlockchainTransaction.TransactionType type);
} 