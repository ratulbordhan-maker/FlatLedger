package com.flatmeals.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Plain unit test: no Spring context, no database. */
class TokenServiceTest {

    private static SecretKey key(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static JwtDecoder decoder(SecretKey key) {
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private static AppUser user(Long id, Role role, Long memberId) {
        AppUser user = new AppUser("anila", "irrelevant-hash", role, memberId);
        ReflectionTestUtils.setField(user, "id", id); // normally set by the database
        return user;
    }

    @Test
    void tokenContainsUserIdMemberIdAndRole() {
        SecretKey key = key("test-secret-that-is-definitely-32-bytes-or-more");
        TokenService tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), 12);

        TokenService.IssuedToken token = tokenService.issue(user(3L, Role.MANAGER, 7L));
        Jwt jwt = decoder(key).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo("anila");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MANAGER");
        CurrentUser current = CurrentUser.from(jwt);
        assertThat(current.userId()).isEqualTo(3L);
        assertThat(current.memberId()).isEqualTo(7L);
        assertThat(current.manager()).isTrue();
        assertThat(token.expiresAt()).isAfter(Instant.now().plusSeconds(11 * 3600));
    }

    @Test
    void memberTokenIsNotManager() {
        SecretKey key = key("test-secret-that-is-definitely-32-bytes-or-more");
        TokenService tokenService = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), 12);

        Jwt jwt = decoder(key).decode(tokenService.issue(user(4L, Role.MEMBER, 8L)).value());

        assertThat(CurrentUser.from(jwt).manager()).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        SecretKey realKey = key("test-secret-that-is-definitely-32-bytes-or-more");
        SecretKey attackerKey = key("some-other-secret-also-32-bytes-or-more-long!!");
        TokenService attacker = new TokenService(new NimbusJwtEncoder(new ImmutableSecret<>(attackerKey)), 12);
        String forged = attacker.issue(user(1L, Role.MANAGER, 1L)).value();

        assertThatThrownBy(() -> decoder(realKey).decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new SecurityConfig("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
