package com.flatmeals.security;

import com.flatmeals.security.AuthDtos.ChangePasswordRequest;
import com.flatmeals.security.AuthDtos.LoginRequest;
import com.flatmeals.security.AuthDtos.LoginResponse;
import com.flatmeals.security.AuthDtos.MeResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

/**
 *   POST /api/auth/login             anyone      → token (429 after 5 wrong passwords in 15 minutes)
 *   GET  /api/auth/me                logged in   → my details
 *   POST /api/auth/change-password   logged in
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LoginAttemptLimiter limiter;

    public AuthController(AuthService authService, LoginAttemptLimiter limiter) {
        this.authService = authService;
        this.limiter = limiter;
    }

    /** Too many wrong passwords in a row → 429 "Too Many Requests" for a while (see LoginAttemptLimiter). */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Duration wait = limiter.waitTime(request.username());
        if (!wait.isZero() && !wait.isNegative()) {
            long minutes = Math.max(1, (wait.toSeconds() + 59) / 60);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong passwords. Try again in " + minutes + (minutes == 1 ? " minute." : " minutes."));
        }
        try {
            LoginResponse response = authService.login(request.username(), request.password());
            limiter.recordSuccess(request.username());
            return response;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                limiter.recordFailure(request.username());
            }
            throw ex;
        }
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(CurrentUser.from(jwt));
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        authService.changePassword(CurrentUser.from(jwt), request.currentPassword(), request.newPassword());
    }
}
