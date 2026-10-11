package com.flatmeals.payment;

import com.flatmeals.common.RecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMonthIdOrderByPaidOnAscIdAsc(Long monthId);

    Optional<Payment> findByIdAndMonthId(Long id, Long monthId);

    /** Used to catch the same payment being entered twice. */
    boolean existsByMonthIdAndMemberIdAndPaidOnAndAmountAndTypeAndStatus(
            Long monthId, Long memberId, LocalDate paidOn, BigDecimal amount, PaymentType type, RecordStatus status);
}
