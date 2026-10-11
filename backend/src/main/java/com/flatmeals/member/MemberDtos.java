package com.flatmeals.member;

import com.flatmeals.security.AppUser;
import com.flatmeals.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** The JSON shapes sent to and from the /api/members endpoints. */
public final class MemberDtos {

    private MemberDtos() {
    }

    public record CreateMemberRequest(
            @NotBlank @Size(max = 100) String name,
            LocalDate joinedOn) {          // optional: today if missing
    }

    public record RenameMemberRequest(
            @NotBlank @Size(max = 100) String name) {
    }

    public record DeactivateMemberRequest(
            LocalDate leftOn) {            // optional: today if missing
    }

    public record CreateLoginRequest(
            @NotBlank
            @Pattern(regexp = "[A-Za-z0-9._-]{3,50}",
                    message = "3-50 characters: letters, numbers, dot, dash or underscore")
            String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            Role role) {                   // optional: MEMBER if missing
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String newPassword) {
    }

    public record MemberResponse(
            Long id,
            String name,
            MemberStatus status,
            LocalDate joinedOn,
            LocalDate leftOn) {

        public static MemberResponse from(Member member) {
            return new MemberResponse(member.getId(), member.getName(), member.getStatus(),
                    member.getJoinedOn(), member.getLeftOn());
        }
    }

    /** Login details shown to the manager. Never includes the password hash. */
    public record LoginAccountResponse(
            Long userId,
            String username,
            Role role,
            Long memberId,
            boolean enabled) {

        public static LoginAccountResponse from(AppUser user) {
            return new LoginAccountResponse(user.getId(), user.getUsername(), user.getRole(),
                    user.getMemberId(), user.isEnabled());
        }
    }
}
