package com.example.eventledger.gateway;

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
class EventRepositoryUniqueConstraintTest {
  private final EventRepository events;

  @Autowired
  EventRepositoryUniqueConstraintTest(EventRepository events) {
    this.events = events;
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void rejectsDuplicateEventIds() {
    events.saveAndFlush(event("gateway-unique-event", "fp-1"));

    assertThatThrownBy(() -> events.saveAndFlush(event("gateway-unique-event", "fp-2")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private EventEntity event(String eventId, String fingerprint) {
    return new EventEntity(
        eventId,
        "acct-repo",
        EventType.CREDIT,
        new BigDecimal("1.00"),
        "USD",
        Instant.parse("2025-01-01T00:00:00Z"),
        fingerprint,
        null);
  }
}
