package com.example.eventledger.account;

import com.example.eventledger.contract.v1.EventType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "applied_transactions")
public class AppliedTransactionEntity {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  @Column(name = "event_id", nullable = false, unique = true, length = 128) private String eventId;
  @Column(name = "account_id", nullable = false, length = 128) private String accountId;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EventType type;
  @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
  @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)") private String currency;
  @Column(nullable = false) private Instant eventTimestamp;
  @Column(nullable = false) private Instant appliedAt;
  @Column(name = "request_fingerprint", nullable = false, length = 64) private String requestFingerprint;

  protected AppliedTransactionEntity() {}

  public AppliedTransactionEntity(String eventId, String accountId, EventType type, BigDecimal amount,
      String currency, Instant eventTimestamp, Instant appliedAt, String requestFingerprint) {
    this.eventId = eventId; this.accountId = accountId; this.type = type; this.amount = amount;
    this.currency = currency; this.eventTimestamp = eventTimestamp; this.appliedAt = appliedAt;
    this.requestFingerprint = requestFingerprint;
  }
  public Long getId() { return id; }
  public String getEventId() { return eventId; }
  public String getAccountId() { return accountId; }
  public EventType getType() { return type; }
  public BigDecimal getAmount() { return amount; }
  public String getCurrency() { return currency; }
  public Instant getEventTimestamp() { return eventTimestamp; }
  public Instant getAppliedAt() { return appliedAt; }
  public String getRequestFingerprint() { return requestFingerprint; }
}
