package com.g18.assistant.mapper;

import com.g18.assistant.dto.response.TelegramMessageResponse;
import com.g18.assistant.entity.TelegramMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TelegramMessageMapper {
    
    @Mapping(target = "shopId", source = "shop.id")
    TelegramMessageResponse toResponse(TelegramMessage message);
    
    List<TelegramMessageResponse> toResponseList(List<TelegramMessage> messages);
} 