package com.g18.assistant.repository;

import com.g18.assistant.entity.TemporaryWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TemporaryWalletRepository extends JpaRepository<TemporaryWallet, Long> {

    Optional<TemporaryWallet> findByWalletAddress(String walletAddress);
    
    List<TemporaryWallet> findByUserId(Long userId);
    
    List<TemporaryWallet> findByStatus(TemporaryWallet.WalletStatus status);
    
    List<TemporaryWallet> findByStatusAndSweptFalse(TemporaryWallet.WalletStatus status);
    
    List<TemporaryWallet> findByExpiresAtBeforeAndStatus(LocalDateTime expiryDate, TemporaryWallet.WalletStatus status);
} 