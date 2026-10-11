package com.flatmeals.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashReceiptRepository extends JpaRepository<CashReceipt, Long> {

    List<CashReceipt> findByMonthIdOrderByReceivedOnAscIdAsc(Long monthId);

    Optional<CashReceipt> findByIdAndMonthId(Long id, Long monthId);
}
