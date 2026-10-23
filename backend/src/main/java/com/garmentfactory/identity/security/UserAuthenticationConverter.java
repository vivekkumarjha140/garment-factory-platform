package com.garmentfactory.identity.security;

import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.identity.service.UserAuthorityService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.security.UserAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Runs after the JWT signature, expiry and issuer are verified. It re-reads the user so a blocked
 * account, or a changed role, stops working immediately rather than when the token expires.
 */
@Component
public class UserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;
    private final UserAuthorityService authorities;

    public UserAuthenticationConverter(UserRepository users, UserAuthorityService authorities) {
        this.users = users;
        this.authorities = authorities;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = users.findByPublicId(jwt.getSubject())
                .orElseThrow(() -> new BadCredentialsException("Unknown token subject"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new DisabledException("Account is not active");
        }
        AuthenticatedUser principal = authorities.principalFor(user);
        return new UserAuthenticationToken(principal, jwt.getTokenValue());
    }
}