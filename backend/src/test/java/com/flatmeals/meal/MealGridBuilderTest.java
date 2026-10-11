package com.flatmeals.meal;

import com.flatmeals.meal.MealGridBuilder.MealGrid;
import com.flatmeals.member.Member;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MealGridBuilderTest {

    private static Member member(Long id, String name) {
        Member member = new Member(name, LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static MealEntry entry(Long memberId, String date, String meals) {
        return new MealEntry(10L, memberId, LocalDate.parse(date), new BigDecimal(meals), 1L);
    }

    @Test
    void octoberHas31ColumnsAndMissingDaysAreZero() {
        MealGrid grid = MealGridBuilder.build(YearMonth.of(2026, 10),
                List.of(member(1L, "Anila"), member(2L, "Srijila")),
                List.of(entry(1L, "2026-10-01", "2"), entry(1L, "2026-10-31", "0.5"), entry(2L, "2026-10-01", "1")));

        assertThat(grid.daysInMonth()).isEqualTo(31);
        assertThat(grid.rows()).hasSize(2);
        assertThat(grid.rows().get(0).meals()).hasSize(31);
        assertThat(grid.rows().get(0).meals().get(0)).isEqualByComparingTo("2.00");
        assertThat(grid.rows().get(0).meals().get(1)).isEqualByComparingTo("0.00");   // nothing stored
        assertThat(grid.rows().get(0).meals().get(30)).isEqualByComparingTo("0.50");  // 31 October
        assertThat(grid.rows().get(0).total()).isEqualByComparingTo("2.50");
        assertThat(grid.rows().get(1).total()).isEqualByComparingTo("1.00");
        assertThat(grid.dayTotals().get(0)).isEqualByComparingTo("3.00");             // 1 October, both members
        assertThat(grid.totalMeals()).isEqualByComparingTo("3.50");
    }

    @Test
    void leapYearFebruaryHas29Columns() {
        MealGrid grid = MealGridBuilder.build(YearMonth.of(2028, 2), List.of(member(1L, "Anila")),
                List.of(entry(1L, "2028-02-29", "2")));

        assertThat(grid.daysInMonth()).isEqualTo(29);
        assertThat(grid.rows().get(0).meals()).hasSize(29);
        assertThat(grid.rows().get(0).meals().get(28)).isEqualByComparingTo("2.00");
    }

    @Test
    void normalFebruaryHas28Columns() {
        MealGrid grid = MealGridBuilder.build(YearMonth.of(2026, 2), List.of(member(1L, "Anila")), List.of());

        assertThat(grid.daysInMonth()).isEqualTo(28);
        assertThat(grid.totalMeals()).isEqualByComparingTo("0.00");
    }

    @Test
    void entriesForOtherMembersOrMonthsAreIgnored() {
        MealGrid grid = MealGridBuilder.build(YearMonth.of(2026, 10), List.of(member(1L, "Anila")),
                List.of(entry(99L, "2026-10-01", "2"), entry(1L, "2026-11-01", "2")));

        assertThat(grid.totalMeals()).isEqualByComparingTo("0.00");
    }
}
