package com.garmentfactory.shared.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Authentication produced after a JWT has been verified and the user re-checked in the database. */
public class UserAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedUser principal;
    private final String tokenValue;

    public UserAuthenticationToken(AuthenticatedUser principal, String tokenValue) {
        super(principal.authorities());
        this.principal = principal;
        this.tokenValue = tokenValue;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return tokenValue;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.publicId();
    }
}