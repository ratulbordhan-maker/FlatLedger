package com.flatmeals.member;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.security.AppUser;
import com.flatmeals.security.CurrentUser;
import com.flatmeals.security.Role;
import com.flatmeals.security.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business rules for flat members and their logins.
 *
 * @Transactional: everything inside one method is saved together, or nothing
 * is saved if an error happens halfway (including the audit row).
 * Inside a transaction, changes to a loaded entity (e.g. member.rename(...))
 * are saved automatically when the method ends.
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public MemberService(MemberRepository memberRepository, UserRepository userRepository,
                         PasswordEncoder passwordEncoder, AuditService auditService) {
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Member> list(MemberStatus status) {
        return (status == null)
                ? memberRepository.findAllByOrderByNameAsc()
                : memberRepository.findByStatusOrderByNameAsc(status);
    }

    @Transactional(readOnly = true)
    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Member " + id + " not found"));
    }

    @Transactional
    public Member create(String name, LocalDate joinedOn, CurrentUser actor) {
        String cleanName = name.trim();
        if (memberRepository.existsByNameIgnoreCase(cleanName)) {
            throw new ConflictException("A member named '" + cleanName + "' already exists");
        }
        Member member = memberRepository.save(
                new Member(cleanName, joinedOn != null ? joinedOn : LocalDate.now()));
        auditService.record(actor.userId(), "MEMBER", member.getId(), AuditAction.CREATE,
                null, snapshot(member), null);
        return member;
    }

    @Transactional
    public Member rename(Long id, String newName, CurrentUser actor) {
        Member member = get(id);
        String cleanName = newName.trim();
        if (memberRepository.existsByNameIgnoreCaseAndIdNot(cleanName, id)) {
            throw new ConflictException("A member named '" + cleanName + "' already exists");
        }
        Map<String, Object> before = snapshot(member);
        member.rename(cleanName);
        auditService.record(actor.userId(), "MEMBER", id, AuditAction.UPDATE,
                before, snapshot(member), "Renamed");
        return member;
    }

    /**
     * Marks a member as left. Nothing is deleted: their meals, payments and
     * balances stay in every month they were part of.
     */
    @Transactional
    public Member deactivate(Long id, LocalDate leftOn, CurrentUser actor) {
        Member member = get(id);
        if (!member.isActive()) {
            throw new ConflictException(member.getName() + " is already inactive");
        }
        if (id.equals(actor.memberId())) {
            throw new ConflictException("You cannot deactivate yourself while you are the manager");
        }
        Map<String, Object> before = snapshot(member);
        member.deactivate(leftOn != null ? leftOn : LocalDate.now());
        auditService.record(actor.userId(), "MEMBER", id, AuditAction.UPDATE,
                before, snapshot(member), "Deactivated");
        return member;
    }

    @Transactional
    public Member reactivate(Long id, CurrentUser actor) {
        Member member = get(id);
        if (member.isActive()) {
            throw new ConflictException(member.getName() + " is already active");
        }
        Map<String, Object> before = snapshot(member);
        member.reactivate();
        auditService.record(actor.userId(), "MEMBER", id, AuditAction.UPDATE,
                before, snapshot(member), "Reactivated");
        return member;
    }

    /** Creates a login so a member can sign in. One login per member. */
    @Transactional
    public AppUser createLogin(Long memberId, String username, String password, Role role, CurrentUser actor) {
        Member member = get(memberId);
        if (userRepository.findByMemberId(memberId).isPresent()) {
            throw new ConflictException(member.getName() + " already has a login");
        }
        String cleanUsername = username.trim();
        if (userRepository.existsByUsernameIgnoreCase(cleanUsername)) {
            throw new ConflictException("Username '" + cleanUsername + "' is already taken");
        }
        AppUser user = userRepository.save(new AppUser(cleanUsername, passwordEncoder.encode(password),
                role != null ? role : Role.MEMBER, memberId));

        Map<String, Object> details = new LinkedHashMap<>();   // no password data in the audit log
        details.put("username", user.getUsername());
        details.put("role", user.getRole().name());
        details.put("memberId", memberId);
        auditService.record(actor.userId(), "USER", user.getId(), AuditAction.CREATE, null, details, null);
        return user;
    }

    @Transactional
    public void resetPassword(Long memberId, String newPassword, CurrentUser actor) {
        AppUser user = userRepository.findByMemberId(memberId)
                .orElseThrow(() -> new NotFoundException("Member " + memberId + " has no login"));
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        auditService.record(actor.userId(), "USER", user.getId(), AuditAction.UPDATE,
                null, null, "Password reset by manager");
    }

    /** The values we store in the audit log for a member. */
    private Map<String, Object> snapshot(Member member) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", member.getName());
        values.put("status", member.getStatus().name());
        values.put("joinedOn", String.valueOf(member.getJoinedOn()));
        values.put("leftOn", member.getLeftOn() == null ? null : member.getLeftOn().toString());
        return values;
    }
}
