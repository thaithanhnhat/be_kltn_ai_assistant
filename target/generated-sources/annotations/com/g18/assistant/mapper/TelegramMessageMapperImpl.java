package com.g18.assistant.mapper;

import com.g18.assistant.dto.response.TelegramMessageResponse;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.TelegramMessage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.2 (Oracle Corporation)"
)
@Component
public class TelegramMessageMapperImpl implements TelegramMessageMapper {

    @Override
    public TelegramMessageResponse toResponse(TelegramMessage message) {
        if ( message == null ) {
            return null;
        }

        TelegramMessageResponse.TelegramMessageResponseBuilder telegramMessageResponse = TelegramMessageResponse.builder();

        telegramMessageResponse.shopId( messageShopId( message ) );
        telegramMessageResponse.id( message.getId() );
        telegramMessageResponse.userId( message.getUserId() );
        telegramMessageResponse.username( message.getUsername() );
        telegramMessageResponse.messageText( message.getMessageText() );
        telegramMessageResponse.chatId( message.getChatId() );
        telegramMessageResponse.fileUrl( message.getFileUrl() );
        telegramMessageResponse.fileType( message.getFileType() );
        telegramMessageResponse.receivedAt( message.getReceivedAt() );
        telegramMessageResponse.processed( message.getProcessed() );

        return telegramMessageResponse.build();
    }

    @Override
    public List<TelegramMessageResponse> toResponseList(List<TelegramMessage> messages) {
        if ( messages == null ) {
            return null;
        }

        List<TelegramMessageResponse> list = new ArrayList<TelegramMessageResponse>( messages.size() );
        for ( TelegramMessage telegramMessage : messages ) {
            list.add( toResponse( telegramMessage ) );
        }

        return list;
    }

    private Long messageShopId(TelegramMessage telegramMessage) {
        if ( telegramMessage == null ) {
            return null;
        }
        Shop shop = telegramMessage.getShop();
        if ( shop == null ) {
            return null;
        }
        Long id = shop.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
