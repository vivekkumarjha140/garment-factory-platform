package com.garmentfactory.shared.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * The authenticated principal available through {@code @AuthenticationPrincipal}.
 * Roles become {@code ROLE_<code>} authorities, permissions are used as-is.
 */
public record AuthenticatedUser(
        long id,
        String publicId,
        String email,
        String fullName,
        Set<String> roles,
        Set<String> permissions) {

    public List<GrantedAuthority> authorities() {
        return Stream.concat(
                        roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
                        permissions.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new))
                .toList();
    }
}