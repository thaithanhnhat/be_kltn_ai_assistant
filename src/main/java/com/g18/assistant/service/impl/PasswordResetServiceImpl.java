package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.ForgotPasswordRequest;
import com.g18.assistant.dto.request.ResetPasswordRequest;
import com.g18.assistant.entity.User;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.service.EmailService;
import com.g18.assistant.service.PasswordResetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String PASSWORD_RESET_PREFIX = "password_reset:";
    
    @Value("${app.verification.token.expiration}")
    private long tokenExpirationSeconds;

    public PasswordResetServiceImpl(
        UserRepository userRepository,
        EmailService emailService,
        PasswordEncoder passwordEncoder,
        @Qualifier("redisTemplateString") RedisTemplate<String, String> redisTemplate) {
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByUsername(request.getEmail())
                .orElse(null);
        
        if (user == null) {
            // User not found, but don't reveal this to the client
            log.info("Password reset requested for non-existent email: {}", request.getEmail());
            return false;
        }
        
        // Generate a token and associate it with the user
        String token = generatePasswordResetToken(user);
        
        try {
            // Send password reset email
            return emailService.sendPasswordResetEmail(user.getUsername(), token).get();
        } catch (Exception e) {
            log.error("Error sending password reset email: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean resetPassword(ResetPasswordRequest request) {
        // Validate password == confirmPassword
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        
        // Get user by token
        User user = getUserByPasswordResetToken(request.getToken());
        if (user == null) {
            throw new IllegalArgumentException("Invalid or expired token");
        }
        
        try {
            // Update the user's password
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
            
            // Delete the token to prevent reuse
            deletePasswordResetToken(request.getToken());
            
            return true;
        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage());
            throw new RuntimeException("Error resetting password", e);
        }
    }

    @Override
    public String generatePasswordResetToken(User user) {
        String token = UUID.randomUUID().toString();
        String key = PASSWORD_RESET_PREFIX + token;
        
        // Store the user's email in Redis with the token as the key
        redisTemplate.opsForValue().set(key, user.getUsername(), tokenExpirationSeconds, TimeUnit.SECONDS);
        
        return token;
    }

    @Override
    public User getUserByPasswordResetToken(String token) {
        String key = PASSWORD_RESET_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        
        if (email == null) {
            return null;
        }
        
        return userRepository.findByUsername(email).orElse(null);
    }
    
    private void deletePasswordResetToken(String token) {
        String key = PASSWORD_RESET_PREFIX + token;
        redisTemplate.delete(key);
    }

    @Override
    public boolean sendPasswordResetEmail(String email) {
        User user = userRepository.findByUsername(email)
                .orElse(null);
        
        if (user == null) {
            // User not found, but don't reveal this to the client
            log.info("Password reset requested for non-existent email: {}", email);
            return false;
        }
        
        // Generate a token and associate it with the user
        String token = generatePasswordResetToken(user);
        
        try {
            // Send password reset email
            return emailService.sendPasswordResetEmail(user.getUsername(), token).get();
        } catch (Exception e) {
            log.error("Error sending password reset email: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isValidPasswordResetToken(String token) {
        String key = PASSWORD_RESET_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);
        
        return email != null && userRepository.findByUsername(email).isPresent();
    }
} 