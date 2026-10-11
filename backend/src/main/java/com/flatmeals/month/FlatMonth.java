package com.flatmeals.month;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One accounting month of the flat (table "months").
 * Named FlatMonth so it doesn't clash with Java's own java.time.Month.
 *
 * totalMeals, totalExpense and mealRate stay empty while the month is OPEN
 * (they are calculated live) and are frozen when the month is closed (Phase 6).
 */
@Entity
@Table(name = "months")
public class FlatMonth {

    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer monthNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MonthStatus status = MonthStatus.OPEN;

    @Column(name = "total_meals", precision = 8, scale = 2)
    private BigDecimal totalMeals;

    @Column(name = "total_expense", precision = 12, scale = 2)
    private BigDecimal totalExpense;

    @Column(name = "meal_rate", precision = 14, scale = 6)
    private BigDecimal mealRate;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected FlatMonth() {
        // required by JPA
    }

    public FlatMonth(int year, int monthNumber) {
        this.year = year;
        this.monthNumber = monthNumber;
    }

    public boolean isOpen() {
        return status == MonthStatus.OPEN;
    }

    public YearMonth yearMonth() {
        return YearMonth.of(year, monthNumber);
    }

    /** e.g. "October 2026" */
    public String label() {
        return yearMonth().format(LABEL);
    }

    public int daysInMonth() {
        return yearMonth().lengthOfMonth();
    }

    /** Throws if the date is missing or falls outside this month. */
    public void requireContains(LocalDate date, String fieldName) {
        if (date == null || !YearMonth.from(date).equals(yearMonth())) {
            throw new IllegalArgumentException(fieldName + " must be a date in " + label());
        }
    }

    public Long getId() { return id; }
    public Integer getYear() { return year; }
    public Integer getMonthNumber() { return monthNumber; }
    public MonthStatus getStatus() { return status; }
    public BigDecimal getTotalMeals() { return totalMeals; }
    public BigDecimal getTotalExpense() { return totalExpense; }
    public BigDecimal getMealRate() { return mealRate; }
    public Instant getClosedAt() { return closedAt; }
    public Long getClosedBy() { return closedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
