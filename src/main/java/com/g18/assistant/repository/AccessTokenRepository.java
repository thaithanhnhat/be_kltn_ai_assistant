package com.g18.assistant.repository;

import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessTokenRepository extends JpaRepository<AccessToken, Long> {
    
    List<AccessToken> findByUser(User user);
    
    List<AccessToken> findByUserAndStatus(User user, AccessToken.TokenStatus status);
    
    List<AccessToken> findByUserAndMethod(User user, AccessToken.TokenMethod method);
    
    Optional<AccessToken> findByUserAndMethodAndStatus(User user, AccessToken.TokenMethod method, AccessToken.TokenStatus status);
    
    List<AccessToken> findByShop(Shop shop);
    
    List<AccessToken> findByShopAndStatus(Shop shop, AccessToken.TokenStatus status);
    
    List<AccessToken> findByShopAndMethod(Shop shop, AccessToken.TokenMethod method);
    
    Optional<AccessToken> findByShopAndMethodAndStatus(Shop shop, AccessToken.TokenMethod method, AccessToken.TokenStatus status);
} 