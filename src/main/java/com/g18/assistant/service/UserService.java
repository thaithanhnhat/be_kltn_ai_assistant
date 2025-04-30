package com.g18.assistant.service;

import com.g18.assistant.dto.request.TokenRefreshRequest;
import com.g18.assistant.dto.request.UserLoginRequest;
import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.dto.request.UserProfileUpdateRequest;
import com.g18.assistant.dto.response.AuthResponse;
import com.g18.assistant.dto.response.TokenRefreshResponse;
import com.g18.assistant.dto.response.UserResponse;

public interface UserService {
    
    /**
     * Start the registration process by validating the request 
     * and sending a verification email
     * 
     * @param request The registration request
     * @return true if the verification email was sent successfully
     */
    boolean initiateRegistration(UserRegisterRequest request);
    
    /**
     * Complete the registration process by verifying the token
     * and creating the user
     * 
     * @param token The verification token
     * @return The created user
     */
    UserResponse completeRegistration(String token);
    
    /**
     * Resend verification token to the specified email
     * 
     * @param email The email to resend verification to
     * @return true if the verification email was sent successfully
     */
    boolean resendVerificationToken(String email);
    
    /**
     * Log in a user
     * 
     * @param request The login request
     * @return Authentication response with user info and tokens
     */
    AuthResponse login(UserLoginRequest request);
    
    /**
     * Refresh the access token using a valid refresh token
     * 
     * @param request The token refresh request
     * @return A new access token and the same refresh token
     */
    TokenRefreshResponse refreshToken(TokenRefreshRequest request);

    /**
     * Get user by username (email)
     * 
     * @param username The username (email) to find
     * @return The user response
     */
    UserResponse getUserByUsername(String username);

    /**
     * Update user profile
     * 
     * @param username The username of the user to update
     * @param request The update request containing new profile data
     * @return The updated user response
     */
    UserResponse updateProfile(String username, UserProfileUpdateRequest request);
    
    /**
     * Get the balance of a user by username
     * 
     * @param username The username of the user
     * @return The user's current balance in VND
     */
    Double getUserBalance(String username);
} 