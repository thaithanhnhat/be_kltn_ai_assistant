package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.AccessTokenRequest;
import com.g18.assistant.dto.response.AccessTokenResponse;
import com.g18.assistant.entity.AccessToken;
import com.g18.assistant.entity.Shop;
import com.g18.assistant.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.2 (Oracle Corporation)"
)
@Component
public class AccessTokenMapperImpl implements AccessTokenMapper {

    @Override
    public AccessToken toEntity(AccessTokenRequest request, User user, Shop shop) {
        if ( request == null && user == null && shop == null ) {
            return null;
        }

        AccessToken.AccessTokenBuilder accessToken = AccessToken.builder();

        if ( request != null ) {
            accessToken.accessToken( request.getAccessToken() );
            accessToken.method( request.getMethod() );
        }
        accessToken.user( user );
        accessToken.shop( shop );
        accessToken.status( AccessToken.TokenStatus.ACTIVE );

        return accessToken.build();
    }

    @Override
    public AccessTokenResponse toResponse(AccessToken accessToken) {
        if ( accessToken == null ) {
            return null;
        }

        AccessTokenResponse.AccessTokenResponseBuilder accessTokenResponse = AccessTokenResponse.builder();

        accessTokenResponse.userId( accessTokenUserId( accessToken ) );
        accessTokenResponse.shopId( accessTokenShopId( accessToken ) );
        accessTokenResponse.id( accessToken.getId() );
        accessTokenResponse.accessToken( accessToken.getAccessToken() );
        accessTokenResponse.status( accessToken.getStatus() );
        accessTokenResponse.method( accessToken.getMethod() );

        return accessTokenResponse.build();
    }

    @Override
    public void updateEntity(AccessToken accessToken, AccessTokenRequest request) {
        if ( request == null ) {
            return;
        }

        accessToken.setAccessToken( request.getAccessToken() );
        accessToken.setMethod( request.getMethod() );
    }

    private Long accessTokenUserId(AccessToken accessToken) {
        if ( accessToken == null ) {
            return null;
        }
        User user = accessToken.getUser();
        if ( user == null ) {
            return null;
        }
        Long id = user.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private Long accessTokenShopId(AccessToken accessToken) {
        if ( accessToken == null ) {
            return null;
        }
        Shop shop = accessToken.getShop();
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
