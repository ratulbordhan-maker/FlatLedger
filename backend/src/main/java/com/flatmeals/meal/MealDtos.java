package com.flatmeals.meal;

import com.flatmeals.member.MemberStatus;
import com.flatmeals.month.MonthStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** The JSON shapes for the meal grid and meal summary. */
public final class MealDtos {

    private MealDtos() {
    }

    /** One cell to save, e.g. {"memberId": 2, "date": "2026-10-05", "meals": 1.5} */
    public record MealCell(
            @NotNull Long memberId,
            @NotNull LocalDate date,
            @NotNull BigDecimal meals) {
    }

    /** Several cells saved together (the grid sends every cell you changed). */
    public record SaveMealsRequest(
            @NotNull @Size(min = 1, max = 2000) List<@Valid @NotNull MealCell> entries) {
    }

    /** One row of the grid: a member and their meals for each day (index 0 = day 1). */
    public record MemberMealRow(
            Long memberId,
            String name,
            MemberStatus memberStatus,
            List<BigDecimal> meals,
            BigDecimal total) {
    }

    public record MealGridResponse(
            int year,
            int month,
            String label,
            MonthStatus status,
            int daysInMonth,
            List<MemberMealRow> members,
            List<BigDecimal> dayTotals,
            BigDecimal totalMeals) {
    }

    public record SaveMealsResponse(
            int changedCells,
            MealGridResponse grid) {
    }

    public record MemberMealCost(
            Long memberId,
            String name,
            BigDecimal meals,
            BigDecimal cost) {
    }

    /** Live meal rate for a month, calculated with the Phase 2 engine. */
    public record MealSummaryResponse(
            int year,
            int month,
            String label,
            MonthStatus status,
            BigDecimal totalMeals,
            BigDecimal totalExpense,
            BigDecimal mealRate,
            boolean canBeFinalized,
            String warning,
            List<MemberMealCost> members) {
    }
}
