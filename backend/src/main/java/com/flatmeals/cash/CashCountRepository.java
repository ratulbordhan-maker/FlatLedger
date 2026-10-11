package com.flatmeals.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CashCountRepository extends JpaRepository<CashCount, Long> {

    /** Oldest first, so the last one per holder is the latest count. */
    List<CashCount> findByMonthIdOrderByIdAsc(Long monthId);
}
