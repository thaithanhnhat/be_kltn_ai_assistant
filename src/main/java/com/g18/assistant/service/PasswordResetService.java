package com.g18.assistant.service;

import com.g18.assistant.dto.request.ForgotPasswordRequest;
import com.g18.assistant.dto.request.ResetPasswordRequest;
import com.g18.assistant.entity.User;

/**
 * Service for password reset operations
 */
public interface PasswordResetService {
    
    /**
     * Generate a password reset token for the user with the given email
     * and send an email with the reset link
     * 
     * @param request The forgot password request containing the email
     * @return true if the email was sent successfully, false otherwise
     */
    boolean forgotPassword(ForgotPasswordRequest request);
    
    /**
     * Reset the user's password using the reset token
     * 
     * @param request The reset password request containing the token and new password
     * @return true if the password was reset successfully, false otherwise
     */
    boolean resetPassword(ResetPasswordRequest request);
    
    /**
     * Generate a password reset token for a user
     * 
     * @param user The user for whom to generate the token
     * @return The generated token
     */
    String generatePasswordResetToken(User user);
    
    /**
     * Get the user associated with a password reset token
     * 
     * @param token The token to look up
     * @return The user associated with the token, or null if not found
     */
    User getUserByPasswordResetToken(String token);

    /**
     * Generates a password reset token and sends a reset email to the user
     *
     * @param email The email of the user requesting password reset
     * @return true if the email was sent successfully, false otherwise
     */
    boolean sendPasswordResetEmail(String email);

    /**
     * Validates if a password reset token is valid
     *
     * @param token The token to validate
     * @return true if the token is valid, false otherwise
     */
    boolean isValidPasswordResetToken(String token);
} 