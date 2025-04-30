package com.g18.assistant.controller;

import com.g18.assistant.dto.request.ResendVerificationRequest;
import com.g18.assistant.dto.request.TokenRefreshRequest;
import com.g18.assistant.dto.request.UserLoginRequest;
import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.dto.request.ForgotPasswordRequest;
import com.g18.assistant.dto.request.ResetPasswordRequest;
import com.g18.assistant.dto.response.AuthResponse;
import com.g18.assistant.dto.response.TokenRefreshResponse;
import com.g18.assistant.dto.response.UserResponse;
import com.g18.assistant.service.UserService;
import com.g18.assistant.service.VerificationService;
import com.g18.assistant.service.PasswordResetService;
import com.g18.assistant.service.impl.UserServiceImpl;
import com.g18.assistant.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.g18.assistant.service.RateLimiter;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {
    
    private final UserService userService;
    private final VerificationService verificationService;
    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final RateLimiter loginRateLimiter;
    
    @Value("${app.frontend.url}")
    private String frontendUrl;
    
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody UserRegisterRequest request) {
        boolean emailSent = userService.initiateRegistration(request);
        
        Map<String, String> response = new HashMap<>();
        if (emailSent) {
            response.put("message", "Verification email sent. Please check your inbox.");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Failed to send verification email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @GetMapping("/verify")
    public void verifyEmail(@RequestParam String token, HttpServletResponse response) throws IOException {
        log.warn("call verify");
        try {
            UserResponse userResponse = userService.completeRegistration(token);
            // Redirect to frontend with success parameter and email
            String encodedEmail = URLEncoder.encode(userResponse.getUsername(), StandardCharsets.UTF_8.toString());
            response.sendRedirect(frontendUrl + "/verification-success?email=" + encodedEmail);
        } catch (Exception e) {
            // Redirect to frontend with error parameter
            String encodedError = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8.toString());
            // Use a dummy email if available or empty string
            String email = getEmailFromToken(token);
            String encodedEmail = email != null ? URLEncoder.encode(email, StandardCharsets.UTF_8.toString()) : "";
            response.sendRedirect(frontendUrl + "/verification-failed?error=" + encodedError + "&email=" + encodedEmail);
        }
    }
    
    /**
     * API endpoint for token verification when the frontend handles the verification flow
     */
    @PostMapping("/verify-token")
    public ResponseEntity<Map<String, String>> verifyToken(@RequestParam String token) {
        Map<String, String> response = new HashMap<>();
        try {
            UserResponse userResponse = userService.completeRegistration(token);
            response.put("message", "Email verified successfully");
            response.put("email", userResponse.getUsername());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }
    
    @PostMapping("/resend-verification")
    public ResponseEntity<Map<String, String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        boolean emailSent = userService.resendVerificationToken(request.getEmail());
        
        Map<String, String> response = new HashMap<>();
        if (emailSent) {
            response.put("message", "Verification email resent. Please check your inbox.");
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Failed to resend verification email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestHeader(value = "X-Forwarded-For", required = false) String ipAddress,
                                 @Valid @RequestBody UserLoginRequest request) {
        // Use client IP for rate limiting
        String clientIp = ipAddress != null ? ipAddress : "unknown";
        
        // Check if rate limit exceeded
        if (loginRateLimiter.isLimitExceeded(clientIp)) {
            Map<String, Object> errorResponse = new HashMap<>();
            long retryAfter = loginRateLimiter.getRetryAfterSeconds(clientIp);
            errorResponse.put("error", "Too many attempts");
            errorResponse.put("message", "Quá nhiều lần đăng nhập thất bại. Vui lòng thử lại sau " + (retryAfter/60) + " phút.");
            errorResponse.put("retryAfter", retryAfter);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
        }
        
        try {
            AuthResponse response = userService.login(request);
            return ResponseEntity.ok(response);
        } catch (UsernameNotFoundException e) {
            // Rate limit is only incremented on invalid credentials
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "User not found");
            errorResponse.put("message", "Email không tồn tại trong hệ thống");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
        } catch (BadCredentialsException e) {
            // Increment failed attempts counter
            loginRateLimiter.isLimitExceeded(clientIp);
            
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid credentials");
            errorResponse.put("message", "Mật khẩu không chính xác");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("not verified")) {
                Map<String, String> errorResponse = new HashMap<>();
                errorResponse.put("error", "Email not verified");
                errorResponse.put("message", "Email chưa được xác minh. Vui lòng kiểm tra hộp thư để xác minh email.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
            }
            throw e;
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Server error");
            errorResponse.put("message", "Đã xảy ra lỗi. Vui lòng thử lại sau.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        try {
            TokenRefreshResponse response = userService.refreshToken(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Invalid refresh token");
            errorResponse.put("message", "Token làm mới không hợp lệ hoặc đã hết hạn");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
        } catch (Exception e) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Server error");
            errorResponse.put("message", "Đã xảy ra lỗi. Vui lòng thử lại sau.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Helper method to extract email from token if possible
     */
    private String getEmailFromToken(String token) {
        try {
            // Use the UserServiceImpl method to get email from token
            return ((UserServiceImpl) userService).getEmailFromToken(token);
        } catch (Exception e) {
            log.error("Could not extract email from token: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if a verification token is valid without consuming it
     */
    @GetMapping("/check-token")
    public ResponseEntity<Map<String, Object>> checkToken(@RequestParam String token) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get the registration request from Redis
            UserRegisterRequest request = verificationService.getRegistrationRequest(token);
            
            if (request == null) {
                // Try to get just the email to determine if token was used or expired
                String email = ((UserServiceImpl) userService).getEmailFromToken(token);
                
                if (email != null) {
                    // Token exists in email mapping but not in request store - likely used
                    if (userRepository.existsByUsername(email)) {
                        response.put("valid", false);
                        response.put("reason", "TOKEN_USED");
                        response.put("message", "Token đã được sử dụng trước đó");
                    } else {
                        response.put("valid", false);
                        response.put("reason", "TOKEN_EXPIRED");
                        response.put("message", "Token đã hết hạn");
                    }
                } else {
                    // No record of this token at all
                    response.put("valid", false);
                    response.put("reason", "TOKEN_INVALID");
                    response.put("message", "Token không hợp lệ");
                }
            } else {
                // Token is valid
                response.put("valid", true);
                response.put("email", request.getEmail());
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking token validity: {}", e.getMessage());
            response.put("valid", false);
            response.put("reason", "SERVER_ERROR");
            response.put("message", "Lỗi server khi kiểm tra token");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Request password reset (forgot password)
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        boolean emailSent = passwordResetService.forgotPassword(request);
        
        Map<String, String> response = new HashMap<>();
        // Always return success to prevent email enumeration attacks
        response.put("message", "If your email exists in our system, password reset instructions will be sent to your email.");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Reset password using token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        Map<String, String> response = new HashMap<>();
        
        try {
            boolean success = passwordResetService.resetPassword(request);
            
            if (success) {
                response.put("message", "Password has been reset successfully.");
                return ResponseEntity.ok(response);
            } else {
                response.put("error", "Failed to reset password.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } catch (Exception e) {
            log.error("Error resetting password: {}", e.getMessage());
            response.put("error", "An unexpected error occurred. Please try again later.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
} 