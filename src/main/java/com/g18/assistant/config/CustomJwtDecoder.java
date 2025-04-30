package com.g18.assistant.config;

import com.g18.assistant.service.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Header;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {

    private final JwtService jwtService;
    
    @Value("${app.jwt.secret}")
    private String secretKey;

    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            // Parse the token to get both headers and claims
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
            Jws<Claims> parsedJwt = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            
            // Extract headers and claims
            Map<String, Object> header = (Map<String, Object>) parsedJwt.getHeader();
            Claims body = parsedJwt.getBody();
            
            // Convert headers to map
            Map<String, Object> headers = new HashMap<>(header);
            
            // Convert claims to map and handle timestamps
            Map<String, Object> claims = new HashMap<>(body);
            convertTimestampClaims(claims);
            
            // Create Jwt object with headers and claims
            return Jwt.withTokenValue(token)
                    .headers(h -> h.putAll(headers))
                    .claims(c -> c.putAll(claims))
                    .build();
            
        } catch (Exception e) {
            throw new JwtException("Error decoding JWT token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Convert Date or Long timestamp values to Instant for Spring Security compatibility
     */
    private void convertTimestampClaims(Map<String, Object> claims) {
        // Standard JWT timestamp claims
        convertTimestamp(claims, "iat");  // issued at
        convertTimestamp(claims, "exp");  // expiration time
        convertTimestamp(claims, "nbf");  // not before
    }
    
    private void convertTimestamp(Map<String, Object> claims, String claimName) {
        Object value = claims.get(claimName);
        
        if (value == null) {
            return;
        }
        
        Instant instant = null;
        
        if (value instanceof Date) {
            instant = ((Date) value).toInstant();
        } else if (value instanceof Number) {
            // JWT timestamps can be seconds since epoch
            long timestamp = ((Number) value).longValue();
            // Assume seconds if the timestamp is too small to be milliseconds
            if (timestamp < 1_000_000_000_000L) {
                instant = Instant.ofEpochSecond(timestamp);
            } else {
                instant = Instant.ofEpochMilli(timestamp);
            }
        }
        
        if (instant != null) {
            claims.put(claimName, instant);
        }
    }
} 