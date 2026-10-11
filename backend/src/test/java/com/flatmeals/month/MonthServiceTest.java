package com.flatmeals.month;

import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.meal.MealEntryRepository;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import com.flatmeals.member.MemberStatus;
import com.flatmeals.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MonthServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);

    @Mock
    private MonthRepository monthRepository;
    @Mock
    private MonthMemberRepository monthMemberRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MealEntryRepository mealEntryRepository;
    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<List<MonthMember>> monthMembersCaptor;

    private MonthService monthService;

    @BeforeEach
    void setUp() {
        monthService = new MonthService(monthRepository, monthMemberRepository, memberRepository,
                mealEntryRepository, auditService);
    }

    private static Member member(Long id, String name) {
        Member member = new Member(name, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static FlatMonth month(Long id, int year, int monthNumber) {
        FlatMonth flatMonth = new FlatMonth(year, monthNumber);
        ReflectionTestUtils.setField(flatMonth, "id", id);
        return flatMonth;
    }

    /** Pretend the database gives a newly saved month the id 10. */
    private void saveAssignsId() {
        when(monthRepository.save(any(FlatMonth.class))).thenAnswer(call -> {
            FlatMonth saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });
    }

    @Test
    void firstMonthCanBeAnyMonthAndGetsAllActiveMembers() {
        when(monthRepository.existsByYearAndMonthNumber(2026, 10)).thenReturn(false);
        when(monthRepository.findTopByOrderByYearDescMonthNumberDesc()).thenReturn(Optional.empty());
        saveAssignsId();
        when(memberRepository.findByStatusOrderByNameAsc(MemberStatus.ACTIVE))
                .thenReturn(List.of(member(1L, "Anila"), member(2L, "Srijila")));

        FlatMonth opened = monthService.open(2026, 10, MANAGER);

        assertThat(opened.label()).isEqualTo("October 2026");
        assertThat(opened.isOpen()).isTrue();
        verify(monthMemberRepository).saveAll(monthMembersCaptor.capture());
        assertThat(monthMembersCaptor.getValue()).extracting(MonthMember::getMemberId).containsExactly(1L, 2L);
        assertThat(monthMembersCaptor.getValue()).allSatisfy(row -> assertThat(row.getMonthId()).isEqualTo(10L));
    }

    @Test
    void monthsMustBeOpenedInOrder() {
        when(monthRepository.existsByYearAndMonthNumber(2026, 12)).thenReturn(false);
        when(monthRepository.findTopByOrderByYearDescMonthNumberDesc()).thenReturn(Optional.of(month(10L, 2026, 10)));

        assertThatThrownBy(() -> monthService.open(2026, 12, MANAGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("November 2026");
        verify(monthRepository, never()).save(any());
    }

    @Test
    void januaryFollowsDecember() {
        when(monthRepository.existsByYearAndMonthNumber(2027, 1)).thenReturn(false);
        when(monthRepository.findTopByOrderByYearDescMonthNumberDesc()).thenReturn(Optional.of(month(12L, 2026, 12)));
        saveAssignsId();
        when(memberRepository.findByStatusOrderByNameAsc(MemberStatus.ACTIVE)).thenReturn(List.of());

        FlatMonth opened = monthService.open(2027, 1, MANAGER);

        assertThat(opened.label()).isEqualTo("January 2027");
    }

    @Test
    void sameMonthCannotBeOpenedTwice() {
        when(monthRepository.existsByYearAndMonthNumber(2026, 10)).thenReturn(true);

        assertThatThrownBy(() -> monthService.open(2026, 10, MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void impossibleMonthIsRejected() {
        assertThatThrownBy(() -> monthService.open(2026, 13, MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(monthRepository);
    }

    @Test
    void closedMonthCannotBeChanged() {
        FlatMonth closed = month(10L, 2026, 9);
        ReflectionTestUtils.setField(closed, "status", MonthStatus.CLOSED);
        when(monthRepository.findByYearAndMonthNumber(2026, 9)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> monthService.findOpen(2026, 9))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void inactiveMemberCannotBeAddedToAMonth() {
        when(monthRepository.findByYearAndMonthNumber(2026, 10)).thenReturn(Optional.of(month(10L, 2026, 10)));
        Member left = member(5L, "Left");
        left.deactivate(LocalDate.of(2026, 9, 30));
        when(memberRepository.findById(5L)).thenReturn(Optional.of(left));

        assertThatThrownBy(() -> monthService.addMember(2026, 10, 5L, MANAGER))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void memberWithMealsCannotBeRemovedFromAMonth() {
        when(monthRepository.findByYearAndMonthNumber(2026, 10)).thenReturn(Optional.of(month(10L, 2026, 10)));
        when(monthMemberRepository.findByMonthIdAndMemberId(10L, 5L)).thenReturn(Optional.of(new MonthMember(10L, 5L)));
        when(mealEntryRepository.existsByMonthIdAndMemberId(10L, 5L)).thenReturn(true);

        assertThatThrownBy(() -> monthService.removeMember(2026, 10, 5L, MANAGER))
                .isInstanceOf(ConflictException.class);
        verify(monthMemberRepository, never()).delete(any());
    }
}
