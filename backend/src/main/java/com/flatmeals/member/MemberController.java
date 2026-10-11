package com.flatmeals.member;

import com.flatmeals.member.MemberDtos.CreateLoginRequest;
import com.flatmeals.member.MemberDtos.CreateMemberRequest;
import com.flatmeals.member.MemberDtos.DeactivateMemberRequest;
import com.flatmeals.member.MemberDtos.LoginAccountResponse;
import com.flatmeals.member.MemberDtos.MemberResponse;
import com.flatmeals.member.MemberDtos.RenameMemberRequest;
import com.flatmeals.member.MemberDtos.ResetPasswordRequest;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * /api/members
 *
 *   GET    /api/members?status=ACTIVE          any logged-in user
 *   GET    /api/members/{id}                   any logged-in user
 *   POST   /api/members                        MANAGER  add a member
 *   PUT    /api/members/{id}                   MANAGER  rename
 *   POST   /api/members/{id}/deactivate        MANAGER  mark as left (history kept)
 *   POST   /api/members/{id}/reactivate        MANAGER
 *   POST   /api/members/{id}/login             MANAGER  create their login
 *   POST   /api/members/{id}/login/reset-password  MANAGER
 *
 * The controller stays thin: read the request, call the service, shape the response.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<MemberResponse> list(@RequestParam(name = "status", required = false) MemberStatus status) {
        return memberService.list(status).stream().map(MemberResponse::from).toList();
    }

    @GetMapping("/{id}")
    public MemberResponse get(@PathVariable("id") Long id) {
        return MemberResponse.from(memberService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public MemberResponse create(@Valid @RequestBody CreateMemberRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return MemberResponse.from(
                memberService.create(request.name(), request.joinedOn(), CurrentUser.from(jwt)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public MemberResponse rename(@PathVariable("id") Long id,
                                 @Valid @RequestBody RenameMemberRequest request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return MemberResponse.from(memberService.rename(id, request.name(), CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public MemberResponse deactivate(@PathVariable("id") Long id,
                                     @RequestBody(required = false) DeactivateMemberRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        var leftOn = (request == null) ? null : request.leftOn();
        return MemberResponse.from(memberService.deactivate(id, leftOn, CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('MANAGER')")
    public MemberResponse reactivate(@PathVariable("id") Long id, @AuthenticationPrincipal Jwt jwt) {
        return MemberResponse.from(memberService.reactivate(id, CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/login")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public LoginAccountResponse createLogin(@PathVariable("id") Long id,
                                            @Valid @RequestBody CreateLoginRequest request,
                                            @AuthenticationPrincipal Jwt jwt) {
        return LoginAccountResponse.from(memberService.createLogin(
                id, request.username(), request.password(), request.role(), CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/login/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('MANAGER')")
    public void resetPassword(@PathVariable("id") Long id,
                              @Valid @RequestBody ResetPasswordRequest request,
                              @AuthenticationPrincipal Jwt jwt) {
        memberService.resetPassword(id, request.newPassword(), CurrentUser.from(jwt));
    }
}
