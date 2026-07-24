package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.EventRequest;
import com.example.eventledger.contract.v1.ProcessingStatus;
import com.example.eventledger.contract.v1.ProcessingStatusTransitions;
import com.example.eventledger.contract.v1.RequestFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Gateway event lifecycle. Persistence claim/finish transactions never wrap the Account HTTP call.
 */
@Service
public class EventProcessingService {
  private final EventRepository repository;
  private final TransactionTemplate transactions;
  private final AccountServiceClient accountClient;
  private final ObjectMapper mapper;
  private final Counter received;
  private final Counter applied;
  private final Counter replays;
  private final Counter conflicts;
  private final Counter downstreamFailures;
  private final Timer duration;

  public EventProcessingService(
      EventRepository repository,
      TransactionTemplate transactions,
      AccountServiceClient accountClient,
      ObjectMapper mapper,
      MeterRegistry metrics) {
    this.repository = repository;
    this.transactions = transactions;
    this.accountClient = accountClient;
    this.mapper = mapper;
    this.received = metrics.counter("event_ledger_events_received_total");
    this.applied = metrics.counter("event_ledger_events_applied_total");
    this.replays = metrics.counter("event_ledger_duplicate_replays_total");
    this.conflicts = metrics.counter("event_ledger_idempotency_conflicts_total");
    this.downstreamFailures = metrics.counter("event_ledger_downstream_failures_total");
    this.duration = metrics.timer("event_ledger_processing_duration_seconds");
  }

  public ProcessingResult submit(EventRequest request) {
    return duration.record(() -> doSubmit(request));
  }

  private ProcessingResult doSubmit(EventRequest request) {
    String fingerprint = RequestFingerprint.compute(request);
    try {
      // Persistable marks inserts as new so assigned eventIds use INSERT (unique constraint is authority).
      transactions.executeWithoutResult(
          status ->
              repository.saveAndFlush(
                  new EventEntity(
                      request.eventId(),
                      request.accountId(),
                      request.type(),
                      request.amount(),
                      request.currency(),
                      request.eventTimestamp(),
                      fingerprint,
                      writeMetadata(request.metadata()))));
      received.increment();
    } catch (DataIntegrityViolationException ignored) {
      EventEntity current = get(request.eventId());
      if (!current.getFingerprint().equals(fingerprint)) {
        conflicts.increment();
        throw new ConflictException("eventId was submitted with a different payload");
      }
      if (current.getStatus() == ProcessingStatus.APPLIED) {
        replays.increment();
        RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "REPLAYED");
        return new ProcessingResult(current, true, false);
      }
      if (current.getStatus() == ProcessingStatus.PERMANENT_FAILURE) {
        throw new DownstreamPermanentException("event has a permanent failure");
      }
    }

    EventEntity claimed = claim(request.eventId());
    if (claimed.getStatus() == ProcessingStatus.APPLIED) {
      replays.increment();
      RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "REPLAYED");
      return new ProcessingResult(claimed, true, false);
    }

    try {
      accountClient.apply(
          new AccountTransactionRequest(
              claimed.getEventId(),
              claimed.getAccountId(),
              claimed.getType(),
              claimed.getAmount(),
              claimed.getCurrency(),
              claimed.getEventTimestamp(),
              claimed.getFingerprint()));
      EventEntity finished = finish(claimed.getEventId(), ProcessingStatus.APPLIED, null, null);
      applied.increment();
      RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "APPLIED");
      return new ProcessingResult(finished, false, true);
    } catch (HttpClientErrorException.Conflict e) {
      finish(claimed.getEventId(), ProcessingStatus.PERMANENT_FAILURE, "ACCOUNT_CONFLICT", e.getMessage());
      RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "CONFLICT");
      throw new ConflictException("account service rejected the event as a conflict");
    } catch (HttpClientErrorException e) {
      finish(
          claimed.getEventId(),
          ProcessingStatus.PERMANENT_FAILURE,
          "ACCOUNT_" + e.getStatusCode().value(),
          e.getMessage());
      RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "PERMANENT_FAILURE");
      throw new DownstreamPermanentException("account service rejected the event");
    } catch (RuntimeException e) {
      downstreamFailures.increment();
      finish(claimed.getEventId(), ProcessingStatus.RETRYABLE_FAILURE, "ACCOUNT_UNAVAILABLE", e.getMessage());
      RequestAuditLog.info("submit_event", request.eventId(), request.accountId(), "RETRYABLE_FAILURE");
      throw new DownstreamUnavailableException("account service is unavailable", e);
    }
  }

  public EventEntity get(String eventId) {
    return repository
        .findByEventId(eventId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "event not found"));
  }

  public java.util.List<EventEntity> list(String accountId, Pageable page) {
    return repository.findByAccountIdOrderByEventTimestampAscEventIdAsc(accountId, page);
  }

  private EventEntity claim(String eventId) {
    try {
      return transactions.execute(
          status -> {
            EventEntity event = get(eventId);
            if (event.getStatus() == ProcessingStatus.APPLIED) {
              return event;
            }
            if (event.getStatus() == ProcessingStatus.PROCESSING) {
              throw new ProcessingInProgressException();
            }
            if (!ProcessingStatusTransitions.canTransition(event.getStatus(), ProcessingStatus.PROCESSING)) {
              throw new DownstreamPermanentException("event cannot be retried");
            }
            event.claim();
            return repository.saveAndFlush(event);
          });
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new ProcessingInProgressException();
    }
  }

  private EventEntity finish(String eventId, ProcessingStatus status, String code, String message) {
    return transactions.execute(
        s -> {
          EventEntity event = get(eventId);
          if (event.getStatus() == ProcessingStatus.APPLIED) {
            return event;
          }
          if (status == ProcessingStatus.APPLIED) {
            event.applied();
          } else {
            event.failed(status, code, message);
          }
          return repository.saveAndFlush(event);
        });
  }

  private String writeMetadata(Map<String, ?> metadata) {
    try {
      return metadata == null ? null : mapper.writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("metadata cannot be serialized", e);
    }
  }

  public record ProcessingResult(EventEntity event, boolean replay, boolean newlyApplied) {}
}
