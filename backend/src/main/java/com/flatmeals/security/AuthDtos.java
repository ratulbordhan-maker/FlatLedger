package com.flatmeals.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * The JSON shapes sent to and from the /api/auth endpoints.
 * (DTO = Data Transfer Object: what goes over the wire, separate from the database entity.)
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record LoginResponse(
            String token,
            Instant expiresAt,
            String username,
            Role role,
            Long memberId,
            String memberName) {
    }

    public record MeResponse(
            Long userId,
            String username,
            Role role,
            Long memberId,
            String memberName) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            // BCrypt only uses the first 72 bytes, so we cap the length there
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }
}
