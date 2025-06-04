package com.g18.assistant.controller;

import com.g18.assistant.dto.request.UserProfileUpdateRequest;
import com.g18.assistant.dto.request.BalanceDeductionRequest;
import com.g18.assistant.dto.response.UserResponse;
import com.g18.assistant.dto.response.BalanceResponse;
import com.g18.assistant.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserResponse> getProfile(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        UserResponse userResponse = userService.getUserByUsername(username);
        return ResponseEntity.ok(userResponse);
    }

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(@AuthenticationPrincipal Jwt jwt) {
        String username = jwt.getSubject();
        Double balance = userService.getUserBalance(username);
        return ResponseEntity.ok(new BalanceResponse(balance));
    }
    @PostMapping("/balance/deduct")
    public ResponseEntity<Boolean> deductBalance(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody BalanceDeductionRequest request) {
        try {
            String username = jwt.getSubject();
            userService.deductBalance(username, request.getAmount());
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            log.error("Error deducting balance for user: {}", jwt.getSubject(), e);
            return ResponseEntity.ok(false);
        }
    }@PostMapping("/balance/deduct-fixed")
    public ResponseEntity<Boolean> deductFixedAmount(@AuthenticationPrincipal Jwt jwt) {
        try {
            String username = jwt.getSubject();
            userService.deductBalance(username, 520000.0);
            return ResponseEntity.ok(true);
        } catch (Exception e) {
            log.error("Error deducting fixed amount for user: {}", jwt.getSubject(), e);
            return ResponseEntity.ok(false);
        }
    }

    @PutMapping
    public ResponseEntity<UserResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UserProfileUpdateRequest request) {
        String username = jwt.getSubject();
        UserResponse updatedUser = userService.updateProfile(username, request);
        return ResponseEntity.ok(updatedUser);
    }
}