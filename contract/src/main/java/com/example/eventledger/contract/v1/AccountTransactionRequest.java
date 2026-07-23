package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionRequest(
    String eventId,
    String accountId,
    EventType type,
    BigDecimal amount,
    String currency,
    Instant eventTimestamp,
    String fingerprint) {}
