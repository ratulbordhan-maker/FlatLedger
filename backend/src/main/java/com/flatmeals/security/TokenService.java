package com.flatmeals.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Creates the signed login token for a user.
 *
 * Claims (the data inside the token):
 *   sub      username
 *   uid      user id
 *   memberId the flat member this login belongs to
 *   roles    ["MANAGER"] or ["MEMBER"]
 *   iat/exp  issued at / expires at
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final Duration lifetime;

    public TokenService(JwtEncoder jwtEncoder, @Value("${app.jwt.expiry-hours:12}") long expiryHours) {
        this.jwtEncoder = jwtEncoder;
        this.lifetime = Duration.ofHours(expiryHours);
    }

    public IssuedToken issue(AppUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(lifetime);

        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("roles", List.of(user.getRole().name()));
        // A claim value can't be null, so only add the ids when they exist
        if (user.getId() != null) {
            claims.claim("uid", user.getId());
        }
        if (user.getMemberId() != null) {
            claims.claim("memberId", user.getMemberId());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
