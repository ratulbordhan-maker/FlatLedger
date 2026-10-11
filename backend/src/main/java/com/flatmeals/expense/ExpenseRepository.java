package com.flatmeals.expense;

import com.flatmeals.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    List<Expense> findByMonthIdOrderByExpenseDateAscIdAsc(Long monthId);

    Optional<Expense> findByIdAndMonthId(Long id, Long monthId);

    /** Used to catch the same expense being entered twice. */
    boolean existsByMonthIdAndExpenseDateAndAmountAndHolderMemberIdAndStatus(
            Long monthId, LocalDate expenseDate, BigDecimal amount, Long holderMemberId, RecordStatus status);
}
