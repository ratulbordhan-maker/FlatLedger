package com.flatmeals.month;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonthMemberRepository extends JpaRepository<MonthMember, MonthMemberId> {

    List<MonthMember> findByMonthId(Long monthId);

    Optional<MonthMember> findByMonthIdAndMemberId(Long monthId, Long memberId);

    boolean existsByMonthIdAndMemberId(Long monthId, Long memberId);
}
