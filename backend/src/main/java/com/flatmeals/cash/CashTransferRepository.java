package com.flatmeals.cash;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CashTransferRepository extends JpaRepository<CashTransfer, Long> {

    List<CashTransfer> findByMonthIdOrderByTransferredOnAscIdAsc(Long monthId);

    Optional<CashTransfer> findByIdAndMonthId(Long id, Long monthId);
}
