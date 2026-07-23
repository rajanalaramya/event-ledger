package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.EventType;
import com.example.eventledger.contract.v1.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "events")
public class EventEntity implements Persistable<String> {
  @Id
  @Column(name = "event_id", length = 128)
  private String eventId;

  @Column(name = "account_id", nullable = false, length = 128)
  private String accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false)
  private EventType type;

  @Column(nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "event_timestamp", nullable = false)
  private Instant eventTimestamp;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "request_fingerprint", nullable = false, length = 64)
  private String fingerprint;

  @Lob
  @Column(name = "metadata_json")
  private String metadataJson;

  @Enumerated(EnumType.STRING)
  @Column(name = "processing_status", nullable = false)
  private ProcessingStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "applied_at")
  private Instant appliedAt;

  @Column(name = "last_error_code", length = 64)
  private String lastErrorCode;

  @Column(name = "last_error_message", length = 512)
  private String lastErrorMessage;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  private long version;

  @Transient
  private boolean isNew = true;

  protected EventEntity() {}

  public EventEntity(
      String eventId,
      String accountId,
      EventType type,
      BigDecimal amount,
      String currency,
      Instant eventTimestamp,
      String fingerprint,
      String metadataJson) {
    Instant now = Instant.now();
    this.eventId = eventId;
    this.accountId = accountId;
    this.type = type;
    this.amount = amount;
    this.currency = currency;
    this.eventTimestamp = eventTimestamp;
    this.fingerprint = fingerprint;
    this.metadataJson = metadataJson;
    this.status = ProcessingStatus.RECEIVED;
    this.receivedAt = now;
    this.createdAt = now;
    this.updatedAt = now;
    this.isNew = true;
  }

  @Override
  public String getId() {
    return eventId;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markNotNew() {
    isNew = false;
  }

  @PreUpdate
  void touch() {
    updatedAt = Instant.now();
  }

  public String getEventId() {
    return eventId;
  }

  public String getAccountId() {
    return accountId;
  }

  public EventType getType() {
    return type;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Instant getEventTimestamp() {
    return eventTimestamp;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getMetadataJson() {
    return metadataJson;
  }

  public ProcessingStatus getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public Instant getAppliedAt() {
    return appliedAt;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public void claim() {
    status = ProcessingStatus.PROCESSING;
    attemptCount++;
    lastAttemptAt = Instant.now();
    lastErrorCode = null;
    lastErrorMessage = null;
  }

  public void applied() {
    status = ProcessingStatus.APPLIED;
    appliedAt = Instant.now();
  }

  public void failed(ProcessingStatus status, String code, String message) {
    this.status = status;
    lastErrorCode = code;
    lastErrorMessage =
        message == null ? null : message.substring(0, Math.min(512, message.length()));
  }
}
