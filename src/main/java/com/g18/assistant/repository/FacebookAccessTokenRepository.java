package com.g18.assistant.repository;

import com.g18.assistant.entity.FacebookAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FacebookAccessTokenRepository extends JpaRepository<FacebookAccessToken, Long> {
    Optional<FacebookAccessToken> findByShopId(Long shopId);
    Optional<FacebookAccessToken> findFirstByShopIdAndActive(Long shopId, boolean active);
    Optional<FacebookAccessToken> findByVerifyToken(String verifyToken);
    Optional<FacebookAccessToken> findByPageId(String pageId);
    Optional<FacebookAccessToken> findByPageIdAndActive(String pageId, boolean active);
    List<FacebookAccessToken> findAllByShopIdAndActive(Long shopId, boolean active);
    List<FacebookAccessToken> findAllByShopId(Long shopId);
}