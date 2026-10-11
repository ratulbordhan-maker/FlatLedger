package com.flatmeals.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashAdjustmentRepository extends JpaRepository<CashAdjustment, Long> {

    List<CashAdjustment> findByMonthIdOrderByIdAsc(Long monthId);

    Optional<CashAdjustment> findByIdAndMonthId(Long id, Long monthId);
}
