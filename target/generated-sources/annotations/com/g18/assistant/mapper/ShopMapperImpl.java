package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.ShopRequest;
import com.g18.assistant.dto.response.ShopResponse;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.2 (Oracle Corporation)"
)
@Component
public class ShopMapperImpl implements ShopMapper {

    @Override
    public Shop toEntity(ShopRequest request, User user) {
        if ( request == null && user == null ) {
            return null;
        }

        Shop.ShopBuilder shop = Shop.builder();

        if ( request != null ) {
            shop.name( request.getName() );
        }
        shop.user( user );
        shop.status( Shop.ShopStatus.ACTIVE );

        return shop.build();
    }

    @Override
    public ShopResponse toResponse(Shop shop) {
        if ( shop == null ) {
            return null;
        }

        ShopResponse.ShopResponseBuilder shopResponse = ShopResponse.builder();

        shopResponse.userId( shopUserId( shop ) );
        shopResponse.id( shop.getId() );
        shopResponse.name( shop.getName() );
        shopResponse.status( shop.getStatus() );

        return shopResponse.build();
    }

    @Override
    public void updateEntity(Shop shop, ShopRequest request) {
        if ( request == null ) {
            return;
        }

        shop.setName( request.getName() );
    }

    private Long shopUserId(Shop shop) {
        if ( shop == null ) {
            return null;
        }
        User user = shop.getUser();
        if ( user == null ) {
            return null;
        }
        Long id = user.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
