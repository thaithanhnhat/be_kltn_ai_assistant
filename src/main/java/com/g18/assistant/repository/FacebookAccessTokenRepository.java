package com.g18.assistant.repository;

import com.g18.assistant.entity.FacebookAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FacebookAccessTokenRepository extends JpaRepository<FacebookAccessToken, Long> {
    Optional<FacebookAccessToken> findByShopId(Long shopId);
    Optional<FacebookAccessToken> findByShopIdAndActive(Long shopId, boolean active);
    Optional<FacebookAccessToken> findByVerifyToken(String verifyToken);
} 