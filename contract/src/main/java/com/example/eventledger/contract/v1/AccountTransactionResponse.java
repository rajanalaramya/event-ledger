package com.example.eventledger.contract.v1;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountTransactionResponse(
    String eventId,
    String accountId,
    EventType type,
    BigDecimal amount,
    String currency,
    BigDecimal balanceAfter,
    Instant appliedAt,
    boolean idempotentReplay) {}
