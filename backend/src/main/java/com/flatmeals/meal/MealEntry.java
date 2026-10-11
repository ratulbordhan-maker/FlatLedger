package com.flatmeals.meal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One cell of the meal grid: how many meals a member had on one date
 * (table meal_entries). A missing row simply means 0 meals.
 */
@Entity
@Table(name = "meal_entries")
public class MealEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "meal_date", nullable = false)
    private LocalDate mealDate;

    @Column(name = "meals", nullable = false, precision = 4, scale = 2)
    private BigDecimal meals;

    @Column(name = "updated_by")
    private Long updatedBy;

    // MySQL sets this automatically (ON UPDATE CURRENT_TIMESTAMP)
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected MealEntry() {
        // required by JPA
    }

    public MealEntry(Long monthId, Long memberId, LocalDate mealDate, BigDecimal meals, Long updatedBy) {
        this.monthId = monthId;
        this.memberId = memberId;
        this.mealDate = mealDate;
        this.meals = meals;
        this.updatedBy = updatedBy;
    }

    public void changeMeals(BigDecimal newMeals, Long userId) {
        this.meals = newMeals;
        this.updatedBy = userId;
    }

    public Long getId() { return id; }
    public Long getMonthId() { return monthId; }
    public Long getMemberId() { return memberId; }
    public LocalDate getMealDate() { return mealDate; }
    public BigDecimal getMeals() { return meals; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
