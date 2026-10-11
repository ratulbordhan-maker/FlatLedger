package com.flatmeals.meal;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.meal.MealDtos.MealCell;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.member.Member;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthMember;
import com.flatmeals.month.MonthMemberRepository;
import com.flatmeals.month.MonthRepository;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {

    private static final CurrentUser MANAGER = new CurrentUser(1L, 1L, "manager", true);
    private static final Long MONTH_ID = 10L;

    @Mock
    private MonthService monthService;
    @Mock
    private MonthRepository monthRepository;
    @Mock
    private MonthMemberRepository monthMemberRepository;
    @Mock
    private MealEntryRepository mealEntryRepository;
    @Mock
    private AuditService auditService;

    @Captor
    private ArgumentCaptor<List<MealEntry>> insertedCaptor;

    private MealService mealService;
    private FlatMonth october;

    @BeforeEach
    void setUp() {
        mealService = new MealService(monthService, monthRepository, monthMemberRepository,
                mealEntryRepository, auditService);
        october = new FlatMonth(2026, 10);
        ReflectionTestUtils.setField(october, "id", MONTH_ID);
    }

    private static MealCell cell(long memberId, String date, String meals) {
        return new MealCell(memberId, LocalDate.parse(date), new BigDecimal(meals));
    }

    private static Member member(Long id, String name) {
        Member member = new Member(name, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    /** October is open and has members 1 and 2. */
    private void openOctoberWithMembers1And2() {
        when(monthService.findOpen(2026, 10)).thenReturn(october);
        when(monthMemberRepository.findByMonthId(MONTH_ID))
                .thenReturn(List.of(new MonthMember(MONTH_ID, 1L), new MonthMember(MONTH_ID, 2L)));
    }

    @Test
    void updatesChangedCellsInsertsNewOnesAndSkipsNewZeros() {
        openOctoberWithMembers1And2();
        MealEntry existing = new MealEntry(MONTH_ID, 1L, LocalDate.of(2026, 10, 1), new BigDecimal("2.00"), 1L);
        when(mealEntryRepository.findByMonthId(MONTH_ID)).thenReturn(List.of(existing));

        int changed = mealService.saveMeals(2026, 10, List.of(
                cell(1, "2026-10-01", "1"),      // existing 2.00 → 1.00 : update
                cell(2, "2026-10-01", "0.5"),    // new cell            : insert
                cell(1, "2026-10-02", "0")),     // new cell with 0     : nothing to store
                MANAGER);

        assertThat(changed).isEqualTo(2);
        assertThat(existing.getMeals()).isEqualByComparingTo("1.00");
        verify(mealEntryRepository).saveAll(insertedCaptor.capture());
        assertThat(insertedCaptor.getValue()).hasSize(1);
        assertThat(insertedCaptor.getValue().get(0).getMemberId()).isEqualTo(2L);
        assertThat(insertedCaptor.getValue().get(0).getMeals()).isEqualByComparingTo("0.50");
        verify(auditService).record(eq(1L), eq("MEAL_ENTRIES"), eq(MONTH_ID), eq(AuditAction.UPDATE),
                isNull(), any(), any());
    }

    @Test
    void savingTheSameValuesChangesNothingAndWritesNoAudit() {
        openOctoberWithMembers1And2();
        MealEntry existing = new MealEntry(MONTH_ID, 1L, LocalDate.of(2026, 10, 1), new BigDecimal("2.00"), 1L);
        when(mealEntryRepository.findByMonthId(MONTH_ID)).thenReturn(List.of(existing));

        int changed = mealService.saveMeals(2026, 10, List.of(cell(1, "2026-10-01", "2")), MANAGER);

        assertThat(changed).isZero();
        verify(mealEntryRepository, never()).saveAll(any());
        verify(auditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void closedMonthIsRefused() {
        when(monthService.findOpen(2026, 9)).thenThrow(new ConflictException("September 2026 is closed"));

        assertThatThrownBy(() -> mealService.saveMeals(2026, 9, List.of(cell(1, "2026-09-01", "2")), MANAGER))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(mealEntryRepository);
    }

    @Test
    void dateOutsideTheMonthIsRefused() {
        openOctoberWithMembers1And2();

        assertThatThrownBy(() -> mealService.saveMeals(2026, 10, List.of(cell(1, "2026-11-01", "2")), MANAGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not in October 2026");
        verifyNoInteractions(mealEntryRepository);
    }

    @Test
    void unsupportedMealValueIsRefused() {
        openOctoberWithMembers1And2();

        assertThatThrownBy(() -> mealService.saveMeals(2026, 10, List.of(cell(1, "2026-10-01", "0.3")), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mealEntryRepository);
    }

    @Test
    void memberNotInTheMonthIsRefused() {
        openOctoberWithMembers1And2();

        assertThatThrownBy(() -> mealService.saveMeals(2026, 10, List.of(cell(7, "2026-10-01", "2")), MANAGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not part of");
    }

    @Test
    void sameCellTwiceInOneRequestIsRefused() {
        openOctoberWithMembers1And2();

        assertThatThrownBy(() -> mealService.saveMeals(2026, 10,
                List.of(cell(1, "2026-10-01", "2"), cell(1, "2026-10-01", "1")), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void oneBadCellMeansNothingIsSaved() {
        openOctoberWithMembers1And2();

        assertThatThrownBy(() -> mealService.saveMeals(2026, 10,
                List.of(cell(1, "2026-10-01", "2"), cell(2, "2026-10-01", "5")), MANAGER))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mealEntryRepository, never()).saveAll(any());
        verifyNoInteractions(auditService);
    }

    @Test
    void summaryUsesOneSharedMealRateAndCostsAddUp() {
        // The worked example: A = 60 meals, B = 45.5, C = 30.25, expenses 12,500.00
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthService.membersOf(october))
                .thenReturn(List.of(member(1L, "A"), member(2L, "B"), member(3L, "C")));
        List<MealEntry> entries = new ArrayList<>();
        for (int day = 1; day <= 30; day++) {
            entries.add(new MealEntry(MONTH_ID, 1L, LocalDate.of(2026, 10, day), new BigDecimal("2"), 1L));
        }
        for (int day = 1; day <= 22; day++) {
            entries.add(new MealEntry(MONTH_ID, 2L, LocalDate.of(2026, 10, day), new BigDecimal("2"), 1L));
        }
        entries.add(new MealEntry(MONTH_ID, 2L, LocalDate.of(2026, 10, 23), new BigDecimal("1.5"), 1L));
        for (int day = 1; day <= 15; day++) {
            entries.add(new MealEntry(MONTH_ID, 3L, LocalDate.of(2026, 10, day), new BigDecimal("2"), 1L));
        }
        entries.add(new MealEntry(MONTH_ID, 3L, LocalDate.of(2026, 10, 16), new BigDecimal("0.25"), 1L));
        when(mealEntryRepository.findByMonthId(MONTH_ID)).thenReturn(entries);
        when(monthRepository.sumMealRateExpenses(MONTH_ID)).thenReturn(new BigDecimal("12500.00"));

        MealSummaryResponse summary = mealService.getSummary(2026, 10);

        assertThat(summary.totalMeals()).isEqualByComparingTo("135.75");
        assertThat(summary.mealRate()).isEqualByComparingTo("92.081031");
        assertThat(summary.canBeFinalized()).isTrue();
        assertThat(summary.members().get(0).cost()).isEqualByComparingTo("5524.86");
        assertThat(summary.members().get(1).cost()).isEqualByComparingTo("4189.69");
        assertThat(summary.members().get(2).cost()).isEqualByComparingTo("2785.45");
    }

    @Test
    void summaryWithNoMealsWarnsInsteadOfCrashing() {
        when(monthService.find(2026, 10)).thenReturn(october);
        when(monthService.membersOf(october)).thenReturn(List.of(member(1L, "A")));
        when(mealEntryRepository.findByMonthId(MONTH_ID)).thenReturn(List.of());
        when(monthRepository.sumMealRateExpenses(MONTH_ID)).thenReturn(new BigDecimal("500.00"));

        MealSummaryResponse summary = mealService.getSummary(2026, 10);

        assertThat(summary.mealRate()).isNull();
        assertThat(summary.canBeFinalized()).isFalse();
        assertThat(summary.warning()).isNotBlank();
    }
}
