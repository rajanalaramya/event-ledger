package com.example.eventledger.account;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppliedTransactionRepository extends JpaRepository<AppliedTransactionEntity, Long> {
  Optional<AppliedTransactionEntity> findByEventId(String eventId);
  List<AppliedTransactionEntity> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId);
}
