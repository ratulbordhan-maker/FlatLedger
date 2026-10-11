package com.flatmeals.meal;

import com.flatmeals.meal.MealDtos.MemberMealRow;
import com.flatmeals.member.Member;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the stored meal entries into a full grid: one row per member,
 * one column per day of the month (28, 29, 30 or 31 columns).
 *
 * Days without an entry show 0.00. Plain Java with no database, so it is easy to test.
 */
public final class MealGridBuilder {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private MealGridBuilder() {
    }

    public record MealGrid(
            int daysInMonth,
            List<MemberMealRow> rows,
            List<BigDecimal> dayTotals,
            BigDecimal totalMeals) {
    }

    /**
     * @param yearMonth the month being shown
     * @param members   the month's members, in the order the rows should appear
     * @param entries   the month's stored meal entries
     */
    public static MealGrid build(YearMonth yearMonth, List<Member> members, Collection<MealEntry> entries) {
        int days = yearMonth.lengthOfMonth();

        // Start every member with a row of zeros
        Map<Long, BigDecimal[]> cellsByMember = new HashMap<>();
        for (Member member : members) {
            BigDecimal[] cells = new BigDecimal[days];
            Arrays.fill(cells, ZERO);
            cellsByMember.put(member.getId(), cells);
        }

        // Fill in the stored entries (ignore anything outside this month or these members)
        for (MealEntry entry : entries) {
            BigDecimal[] cells = cellsByMember.get(entry.getMemberId());
            if (cells == null || !YearMonth.from(entry.getMealDate()).equals(yearMonth)) {
                continue;
            }
            cells[entry.getMealDate().getDayOfMonth() - 1] = entry.getMeals().setScale(2);
        }

        // Build rows, plus totals per day and for the whole month
        BigDecimal[] dayTotals = new BigDecimal[days];
        Arrays.fill(dayTotals, ZERO);
        BigDecimal grandTotal = ZERO;
        List<MemberMealRow> rows = new ArrayList<>();

        for (Member member : members) {
            BigDecimal[] cells = cellsByMember.get(member.getId());
            BigDecimal memberTotal = ZERO;
            for (int day = 0; day < days; day++) {
                memberTotal = memberTotal.add(cells[day]);
                dayTotals[day] = dayTotals[day].add(cells[day]);
            }
            grandTotal = grandTotal.add(memberTotal);
            rows.add(new MemberMealRow(member.getId(), member.getName(), member.getStatus(),
                    List.of(cells), memberTotal));
        }

        return new MealGrid(days, rows, List.of(dayTotals), grandTotal);
    }
}
