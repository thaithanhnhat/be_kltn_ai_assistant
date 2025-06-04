package com.g18.assistant.service.impl;

import com.g18.assistant.dto.request.UserLoginRequest;
import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.dto.request.TokenRefreshRequest;
import com.g18.assistant.dto.request.UserProfileUpdateRequest;
import com.g18.assistant.dto.response.AuthResponse;
import com.g18.assistant.dto.response.UserResponse;
import com.g18.assistant.dto.response.TokenRefreshResponse;
import com.g18.assistant.entity.User;
import com.g18.assistant.mapper.UserMapper;
import com.g18.assistant.repository.UserRepository;
import com.g18.assistant.service.EmailService;
import com.g18.assistant.service.JwtService;
import com.g18.assistant.service.UserService;
import com.g18.assistant.service.VerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final VerificationService verificationService;
    private final JwtService jwtService;
    
    @Qualifier("redisTemplateString")
    private final RedisTemplate<String, String> redisTemplateString;
    
    private static final String EMAIL_TOKEN_PREFIX = "email:";
    private static final long EMAIL_TOKEN_EXPIRATION = 15 * 60; // 15 minutes in seconds
    
    @Override
    public boolean initiateRegistration(UserRegisterRequest request) {
        // Validate password match
        if (!request.getPassword().equals(request.getRePassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }
        
        // Check if email is already used
        if (userRepository.existsByUsername(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }
        
        // Generate verification token and store registration request in Redis
        String token = verificationService.generateVerificationToken(request);
        
        // Also store the email separately for verification page redirection
        storeEmailForToken(token, request.getEmail());
        
        try {
            // Send verification email and wait for result
            return emailService.sendVerificationEmail(request.getEmail(), token).get();
        } catch (Exception e) {
            log.error("Error sending verification email: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Store the email associated with a token for later retrieval
     */
    private void storeEmailForToken(String token, String email) {
        String key = EMAIL_TOKEN_PREFIX + token;
        redisTemplateString.opsForValue().set(key, email, EMAIL_TOKEN_EXPIRATION, TimeUnit.SECONDS);
    }
    
    /**
     * Get the email associated with a token
     */
    public String getEmailFromToken(String token) {
        String key = EMAIL_TOKEN_PREFIX + token;
        return redisTemplateString.opsForValue().get(key);
    }
    
    @Override
    @Transactional
    public UserResponse completeRegistration(String token) {
        // Retrieve registration request from Redis
        UserRegisterRequest request = verificationService.getRegistrationRequest(token);
        if (request == null) {
            throw new IllegalArgumentException("Invalid or expired verification token");
        }
        
        try {
            // Map DTO to entity and set encoded password
            User user = userMapper.toEntity(request);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setVerified(true);
            
            // Save user to database
            User savedUser = userRepository.save(user);
            
            // Delete token from Redis
            verificationService.deleteVerificationToken(token);
            
            // Delete email token mapping
            String key = EMAIL_TOKEN_PREFIX + token;
            redisTemplateString.delete(key);
            
            // Return the mapped response
            return userMapper.toResponse(savedUser);
        } catch (Exception e) {
            log.error("Error completing registration: {}", e.getMessage());
            throw new RuntimeException("Error registering user", e);
        }
    }
    
    @Override
    public boolean resendVerificationToken(String email) {
        // Check if user is already registered
        if (userRepository.existsByUsername(email)) {
            throw new IllegalArgumentException("User is already registered with this email");
        }
        
        // Create a request object with the email
        UserRegisterRequest dummyRequest = new UserRegisterRequest();
        dummyRequest.setEmail(email);
        
        // Check if there's an existing verification in progress
        // Implementation detail: In a more robust system, we would check if there's an 
        // existing verification token for this email in Redis before creating a new one
        
        // Generate new token
        String token = verificationService.generateVerificationToken(dummyRequest);
        
        // Store the email separately for verification page redirection
        storeEmailForToken(token, email);
        
        try {
            // Send verification email and wait for result
            return emailService.sendVerificationEmail(email, token).get();
        } catch (Exception e) {
            log.error("Error resending verification email: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public AuthResponse login(UserLoginRequest request) {
        // Find user by username (email)
        User user = userRepository.findByUsername(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        // Check if user is verified
        if (!user.getVerified()) {
            throw new IllegalStateException("Email not verified. Please verify your email first.");
        }
        
        // Check if password matches
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }
        
        // Generate JWT tokens
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        
        // Create and return response
        return AuthResponse.builder()
                .user(userMapper.toResponse(user))
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(3600) // 1 hour in seconds
                .build();
    }
    
    @Override
    public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
        // Validate the refresh token
        String username = jwtService.validateTokenAndGetUsername(request.getRefreshToken());
        if (username == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        
        // Get the user from the database
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        // Generate a new access token
        String newAccessToken = jwtService.generateAccessToken(user);
        
        // Return the new access token and the existing refresh token
        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(3600) // 1 hour in seconds
                .build();
    }

    @Override
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return userMapper.toResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String username, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        // Update user information
        user.setFullname(request.getFullname());
        
        // If email is being changed, check if it's already in use
        if (!user.getUsername().equals(request.getEmail())) {
            if (userRepository.existsByUsername(request.getEmail())) {
                throw new IllegalArgumentException("Email is already in use");
            }
            user.setUsername(request.getEmail());
        }

        // If password is being updated, validate and update it
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            if (!request.getPassword().equals(request.getRePassword())) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        User updatedUser = userRepository.save(user);
        return userMapper.toResponse(updatedUser);
    }

    @Override
    public Double getUserBalance(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return user.getBalance();
    }
    
    @Override
    @Transactional
    public Double deductBalance(String username, Double amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Deduction amount must be positive");
        }
        
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        Double currentBalance = user.getBalance();
        if (currentBalance < amount) {
            throw new IllegalArgumentException("Insufficient balance. Current balance: " + currentBalance + " VND, Required: " + amount + " VND");
        }
        
        Double newBalance = currentBalance - amount;
        user.setBalance(newBalance);
        userRepository.save(user);
        
        log.info("Deducted {} VND from user {}'s balance. New balance: {} VND", amount, username, newBalance);
        return newBalance;
    }
}