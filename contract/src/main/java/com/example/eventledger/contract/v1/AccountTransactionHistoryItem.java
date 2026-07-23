package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionHistoryItem(
    String eventId,
    EventType type,
    BigDecimal amount,
    String currency,
    Instant eventTimestamp,
    Instant appliedAt) {}
