package com.g18.assistant.dto.response;

import com.g18.assistant.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    
    private Long id;
    private String username;
    private String fullname;
    private LocalDate birthdate;
    private Double balance;
    private Boolean isAdmin;
    private User.UserStatus status;
} 