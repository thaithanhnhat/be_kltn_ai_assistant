package com.g18.assistant.service;

import java.util.concurrent.CompletableFuture;

public interface EmailService {
    
    /**
     * Send a verification email with the provided verification token
     * 
     * @param to Email address to send the verification to
     * @param verificationToken Token to be used for verification
     * @return CompletableFuture containing true if the email was sent successfully
     */
    CompletableFuture<Boolean> sendVerificationEmail(String to, String verificationToken);
    
    /**
     * Send a password reset email with the provided reset token
     * 
     * @param to Email address to send the reset link to
     * @param resetToken Token to be used for password reset
     * @return CompletableFuture containing true if the email was sent successfully
     */
    CompletableFuture<Boolean> sendPasswordResetEmail(String to, String resetToken);
} 