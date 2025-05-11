package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface ShopMapper {
    
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", source = "user")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "accessTokens", ignore = true)
    Shop toEntity(ShopRequest request, User user);
    
    @Mapping(target = "userId", source = "user.id")
    ShopResponse toResponse(Shop shop);
    
    void updateEntity(@MappingTarget Shop shop, ShopRequest request);
} 