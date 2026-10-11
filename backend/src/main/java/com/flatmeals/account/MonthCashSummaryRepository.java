package com.flatmeals.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthCashSummaryRepository extends JpaRepository<MonthCashSummary, MonthHolderId> {

    List<MonthCashSummary> findByMonthId(Long monthId);
}
