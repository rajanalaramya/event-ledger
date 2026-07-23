package com.example.eventledger.gateway;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<EventEntity, String> {
  Optional<EventEntity> findByEventId(String eventId);
  List<EventEntity> findByAccountIdOrderByEventTimestampAscEventIdAsc(String accountId, Pageable pageable);
}
