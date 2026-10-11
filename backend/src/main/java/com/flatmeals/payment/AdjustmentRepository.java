package com.flatmeals.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdjustmentRepository extends JpaRepository<Adjustment, Long> {

    List<Adjustment> findByMonthIdOrderByIdAsc(Long monthId);

    Optional<Adjustment> findByIdAndMonthId(Long id, Long monthId);
}
