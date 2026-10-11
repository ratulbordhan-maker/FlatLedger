package com.flatmeals.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * The logged-in caller, read from their token.
 * Controllers get the token with @AuthenticationPrincipal Jwt jwt and call
 * CurrentUser.from(jwt).
 */
public record CurrentUser(Long userId, Long memberId, String username, boolean manager) {

    public static CurrentUser from(Jwt jwt) {
        Object uid = jwt.getClaim("uid");
        Object memberId = jwt.getClaim("memberId");
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean manager = roles != null && roles.contains(Role.MANAGER.name());
        return new CurrentUser(asLong(uid), asLong(memberId), jwt.getSubject(), manager);
    }

    /** Numbers inside a token may arrive as Integer or Long. */
    private static Long asLong(Object value) {
        return (value instanceof Number number) ? number.longValue() : null;
    }
}
