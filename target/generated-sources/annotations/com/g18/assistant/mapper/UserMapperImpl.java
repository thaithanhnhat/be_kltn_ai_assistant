package com.g18.assistant.mapper;

import com.g18.assistant.dto.request.UserRegisterRequest;
import com.g18.assistant.dto.response.UserResponse;
import com.g18.assistant.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 23.0.2 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toEntity(UserRegisterRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.username( request.getEmail() );
        user.password( request.getPassword() );
        user.fullname( request.getFullname() );

        return user.build();
    }

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.id( user.getId() );
        userResponse.username( user.getUsername() );
        userResponse.fullname( user.getFullname() );
        userResponse.birthdate( user.getBirthdate() );
        userResponse.balance( user.getBalance() );
        userResponse.isAdmin( user.getIsAdmin() );
        userResponse.status( user.getStatus() );

        return userResponse.build();
    }

    @Override
    public void updateEntity(User user, UserRegisterRequest request) {
        if ( request == null ) {
            return;
        }

        user.setPassword( request.getPassword() );
        user.setFullname( request.getFullname() );
    }
}
