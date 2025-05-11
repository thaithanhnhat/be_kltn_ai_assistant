package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.AccessTokenRequest;
import com.g18.assistant.dto.response.AccessTokenResponse;
import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface AccessTokenMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "shop", source = "shop")
    @Mapping(target = "status", constant = "ACTIVE")
    AccessToken toEntity(AccessTokenRequest request, User user, Shop shop);
    
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "shopId", source = "shop.id")
    AccessTokenResponse toResponse(AccessToken accessToken);
    
    void updateEntity(@MappingTarget AccessToken accessToken, AccessTokenRequest request);
} 