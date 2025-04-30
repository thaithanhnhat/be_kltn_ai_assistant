package com.g18.assistant.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errorResponse = new HashMap<>();
        
        if (ex.getBindingResult().hasErrors()) {
            String fieldName = ex.getBindingResult().getFieldErrors().get(0).getField();
            String errorMessage = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
            
            // Kiểm tra nếu là lỗi liên quan đến thông tin đăng nhập
            if (fieldName.equals("email") || fieldName.equals("password")) {
                errorResponse.put("error", "missing_credentials");
                errorResponse.put("message", "Vui lòng cung cấp email và mật khẩu");
            } else {
                errorResponse.put("error", "validation_error");
                errorResponse.put("message", errorMessage);
            }
        } else {
            errorResponse.put("error", "validation_error");
            errorResponse.put("message", "Dữ liệu không hợp lệ");
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "invalid_input");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, String> response = new HashMap<>();
        
        // Kiểm tra nếu là lỗi email chưa xác minh
        if (ex.getMessage().contains("not verified")) {
            response.put("error", "email_not_verified");
            response.put("message", "Email chưa được xác minh. Vui lòng kiểm tra hộp thư để xác minh email.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        
        response.put("error", "invalid_state");
        response.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentialsException(BadCredentialsException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "invalid_credentials");
        response.put("message", "Mật khẩu không chính xác");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
    
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUsernameNotFoundException(UsernameNotFoundException ex) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "user_not_found");
        response.put("message", "Email không tồn tại trong hệ thống");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        Map<String, String> response = new HashMap<>();
        response.put("error", "server_error");
        response.put("message", "Đã xảy ra lỗi từ phía máy chủ. Vui lòng thử lại sau.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
} 