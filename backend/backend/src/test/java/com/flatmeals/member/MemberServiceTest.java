package com.flatmeals.member;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.security.AppUser;
import com.flatmeals.security.CurrentUser;
import com.flatmeals.security.Role;
import com.flatmeals.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for member rules. The repositories are Mockito "mocks":
 * fake objects whose answers we script, so no database is needed.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);

    @Mock
    private MemberRepository memberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, userRepository, passwordEncoder, auditService);
    }

    @Test
    void createTrimsTheNameAndWritesAnAuditRow() {
        when(memberRepository.existsByNameIgnoreCase("Anila")).thenReturn(false);
        when(memberRepository.save(any(Member.class))).thenAnswer(call -> call.getArgument(0));

        Member member = memberService.create("  Anila  ", LocalDate.of(2026, 10, 1), MANAGER);

        assertThat(member.getName()).isEqualTo("Anila");
        assertThat(member.isActive()).isTrue();
        verify(auditService).record(eq(1L), eq("MEMBER"), any(), eq(AuditAction.CREATE), isNull(), any(), isNull());
    }

    @Test
    void duplicateNameIsRejected() {
        when(memberRepository.existsByNameIgnoreCase("Anila")).thenReturn(true);

        assertThatThrownBy(() -> memberService.create("Anila", null, MANAGER))
                .isInstanceOf(ConflictException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void deactivateKeepsTheMemberAndRecordsTheLeavingDate() {
        Member srijila = new Member("Srijila", LocalDate.of(2026, 1, 1));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(srijila));

        memberService.deactivate(5L, LocalDate.of(2026, 10, 10), MANAGER);

        assertThat(srijila.getStatus()).isEqualTo(MemberStatus.INACTIVE);
        assertThat(srijila.getLeftOn()).isEqualTo(LocalDate.of(2026, 10, 10));
        verify(memberRepository, never()).delete(any());
        verify(memberRepository, never()).deleteById(any());
    }

    @Test
    void managerCannotDeactivateThemselves() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(new Member("Manager", LocalDate.of(2026, 1, 1))));

        assertThatThrownBy(() -> memberService.deactivate(1L, null, MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void leavingDateCannotBeBeforeJoiningDate() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(new Member("X", LocalDate.of(2026, 5, 1))));

        assertThatThrownBy(() -> memberService.deactivate(5L, LocalDate.of(2026, 4, 30), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reactivateClearsTheLeavingDate() {
        Member member = new Member("X", LocalDate.of(2026, 1, 1));
        member.deactivate(LocalDate.of(2026, 6, 1));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(member));

        memberService.reactivate(5L, MANAGER);

        assertThat(member.isActive()).isTrue();
        assertThat(member.getLeftOn()).isNull();
    }

    @Test
    void createLoginStoresAHashNeverThePassword() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(new Member("Srijila", LocalDate.of(2026, 1, 1))));
        when(userRepository.findByMemberId(5L)).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("srijila")).thenReturn(false);
        when(userRepository.save(any(AppUser.class))).thenAnswer(call -> call.getArgument(0));

        memberService.createLogin(5L, "srijila", "secret-pass-123", null, MANAGER);

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.MEMBER);   // default role
        assertThat(saved.getValue().getMemberId()).isEqualTo(5L);
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("secret-pass-123");
        assertThat(passwordEncoder.matches("secret-pass-123", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void usernameMustBeUnique() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(new Member("Srijila", LocalDate.of(2026, 1, 1))));
        when(userRepository.findByMemberId(5L)).thenReturn(Optional.empty());
        when(userRepository.existsByUsernameIgnoreCase("anila")).thenReturn(true);

        assertThatThrownBy(() -> memberService.createLogin(5L, "anila", "secret-pass-123", null, MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aMemberCanHaveOnlyOneLogin() {
        when(memberRepository.findById(5L)).thenReturn(Optional.of(new Member("Srijila", LocalDate.of(2026, 1, 1))));
        when(userRepository.findByMemberId(5L))
                .thenReturn(Optional.of(new AppUser("srijila", "hash", Role.MEMBER, 5L)));

        assertThatThrownBy(() -> memberService.createLogin(5L, "srijila2", "secret-pass-123", null, MANAGER))
                .isInstanceOf(ConflictException.class);
    }
}
