package com.example.eventledger.account;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eventledger.contract.v1.EventType;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@ActiveProfiles("test")
class RepositoryUniqueConstraintTest {
  private final AppliedTransactionRepository transactions;

  @Autowired
  RepositoryUniqueConstraintTest(AppliedTransactionRepository transactions) {
    this.transactions = transactions;
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsDuplicateEventIds() {
    transactions.saveAndFlush(transaction("unique-event", "first"));

    assertThatThrownBy(() -> transactions.saveAndFlush(transaction("unique-event", "second")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private AppliedTransactionEntity transaction(String eventId, String fingerprint) {
    Instant now = Instant.parse("2025-01-01T00:00:00Z");
    return new AppliedTransactionEntity(eventId, "repository-account", EventType.CREDIT, new BigDecimal("1.00"),
        "USD", now, now, fingerprint);
  }
}
