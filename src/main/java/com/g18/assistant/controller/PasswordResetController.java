package com.g18.assistant.controller;

import com.g18.assistant.dto.request.ForgotPasswordRequest;
import com.g18.assistant.dto.request.ResetPasswordRequest;
import com.g18.assistant.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
@Slf4j
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    /**
     * Send a password reset email to the user
     */
    @PostMapping("/forgot")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        boolean emailSent = passwordResetService.forgotPassword(request);
        
        Map<String, String> response = new HashMap<>();
        if (emailSent) {
            response.put("message", "Password reset email sent. Please check your inbox.");
            return ResponseEntity.ok(response);
        } else {
            response.put("message", "If an account exists with this email, a password reset link will be sent.");
            return ResponseEntity.ok(response);
        }
    }
    
    /**
     * Validate a password reset token without consuming it
     */
    @GetMapping("/validate-token")
    public ResponseEntity<Map<String, Boolean>> validateResetToken(@RequestParam String token) {
        boolean isValid = passwordResetService.isValidPasswordResetToken(token);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("valid", isValid);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset the user's password using the reset token
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Map<String, String> response = new HashMap<>();
        
        try {
            boolean success = passwordResetService.resetPassword(request);
            if (success) {
                response.put("message", "Password reset successful.");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to reset password.");
                return ResponseEntity.badRequest().body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error resetting password: ", e);
            response.put("error", "An unexpected error occurred.");
            return ResponseEntity.internalServerError().body(response);
        }
    }
} 