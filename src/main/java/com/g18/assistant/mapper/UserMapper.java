package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.dto.response.UserResponse;
import com.g18.assistant.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {
    
    @Mapping(target = "username", source = "email")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "birthdate", ignore = true)
    @Mapping(target = "balance", ignore = true)
    @Mapping(target = "isAdmin", ignore = true)
    @Mapping(target = "status", ignore = true)
    User toEntity(UserRegisterRequest request);
    
    UserResponse toResponse(User user);
    
    void updateEntity(@MappingTarget User user, UserRegisterRequest request);
} 