package com.flatmeals.meal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MealEntryRepository extends JpaRepository<MealEntry, Long> {

    /** All cells of one month (at most ~31 days × number of members). */
    List<MealEntry> findByMonthId(Long monthId);

    boolean existsByMonthIdAndMemberId(Long monthId, Long memberId);
}
