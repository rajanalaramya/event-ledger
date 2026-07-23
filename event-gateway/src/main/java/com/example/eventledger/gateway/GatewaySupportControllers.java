package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.EventRequest;
import com.example.eventledger.contract.v1.ProcessingStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {
  private final ApplicationAvailability availability;
  private final DataSource dataSource;

  HealthController(ApplicationAvailability availability, DataSource dataSource) {
    this.availability = availability;
    this.dataSource = dataSource;
  }

  @GetMapping("/health")
  Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("service", "event-gateway");
    body.put("version", "1.0.0-SNAPSHOT");
    body.put("liveness", availability.getLivenessState().name());
    body.put("database", databaseStatus());
    return body;
  }

  private Map<String, Object> databaseStatus() {
    Map<String, Object> db = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection()) {
      boolean valid = connection.isValid(1);
      db.put("status", valid ? "UP" : "DOWN");
      db.put("product", connection.getMetaData().getDatabaseProductName());
    } catch (Exception ex) {
      db.put("status", "DOWN");
      db.put("error", "database unreachable");
    }
    return db;
  }
}

@RestController
@ConditionalOnProperty(prefix = "eventledger.internal-retry", name = "enabled", havingValue = "true")
class InternalRetryController {
  private final EventProcessingService service;
  private final ObjectMapper mapper;

  InternalRetryController(EventProcessingService service, ObjectMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping("/internal/events/{eventId}/retry")
  ResponseEntity<?> retry(@PathVariable String eventId) throws Exception {
    EventEntity event = service.get(eventId);
    if (event.getStatus() != ProcessingStatus.RETRYABLE_FAILURE) {
      throw new IllegalArgumentException("only retryable failures may be retried");
    }
    Map<String, ?> metadata =
        event.getMetadataJson() == null
            ? null
            : mapper.readValue(event.getMetadataJson(), new TypeReference<Map<String, Object>>() {});
    var result =
        service.submit(
            new EventRequest(
                event.getEventId(),
                event.getAccountId(),
                event.getType(),
                event.getAmount(),
                event.getCurrency(),
                event.getEventTimestamp(),
                metadata));
    return ResponseEntity.ok(EventController.response(result.event(), result.replay()));
  }
}
