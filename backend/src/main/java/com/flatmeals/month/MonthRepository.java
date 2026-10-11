package com.flatmeals.month;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface MonthRepository extends JpaRepository<FlatMonth, Long> {

    Optional<FlatMonth> findByYearAndMonthNumber(Integer year, Integer monthNumber);

    boolean existsByYearAndMonthNumber(Integer year, Integer monthNumber);

    /** The most recent month that has been opened, if any. */
    Optional<FlatMonth> findTopByOrderByYearDescMonthNumberDesc();

    List<FlatMonth> findAllByOrderByYearDescMonthNumberDesc();

    /**
     * Total of the month's expenses that count toward the meal rate.
     * Written as plain SQL because the expense screens arrive in Phase 5;
     * until then it simply returns 0.
     */
    @Query(value = "SELECT COALESCE(SUM(amount), 0.00) FROM expenses "
            + "WHERE month_id = :monthId AND status = 'ACTIVE' AND in_meal_rate = TRUE",
            nativeQuery = true)
    BigDecimal sumMealRateExpenses(@Param("monthId") Long monthId);
}
