package com.flatmeals.account;

import com.flatmeals.month.MonthMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonthMemberSummaryRepository extends JpaRepository<MonthMemberSummary, MonthMemberId> {

    List<MonthMemberSummary> findByMonthId(Long monthId);
}
