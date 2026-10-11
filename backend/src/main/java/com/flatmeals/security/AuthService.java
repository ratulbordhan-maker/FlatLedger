package com.flatmeals.security;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import com.flatmeals.security.AuthDtos.LoginResponse;
import com.flatmeals.security.AuthDtos.MeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Login, "who am I", and changing your own password. */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AuditService auditService;

    public AuthService(UserRepository userRepository, MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder, TokenService tokenService,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    /**
     * Checks the username and password and returns a token.
     * The error message is the same whether the username or the password was
     * wrong, so nobody can use the login form to discover valid usernames.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(String username, String password) {
        AppUser user = userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (user == null || !user.isEnabled() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        TokenService.IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), user.getUsername(),
                user.getRole(), user.getMemberId(), memberName(user));
    }

    @Transactional(readOnly = true)
    public MeResponse me(CurrentUser currentUser) {
        AppUser user = findUser(currentUser);
        return new MeResponse(user.getId(), user.getUsername(), user.getRole(),
                user.getMemberId(), memberName(user));
    }

    @Transactional
    public void changePassword(CurrentUser currentUser, String currentPassword, String newPassword) {
        AppUser user = findUser(currentUser);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        auditService.record(user.getId(), "USER", user.getId(), AuditAction.UPDATE,
                null, null, "Changed own password");
    }

    private AppUser findUser(CurrentUser currentUser) {
        if (currentUser.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again");
        }
        return userRepository.findById(currentUser.userId())
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
    }

    private String memberName(AppUser user) {
        if (user.getMemberId() == null) {
            return null;
        }
        return memberRepository.findById(user.getMemberId()).map(Member::getName).orElse(null);
    }
}
