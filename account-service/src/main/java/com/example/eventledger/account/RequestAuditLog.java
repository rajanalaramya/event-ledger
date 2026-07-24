package com.example.eventledger.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Emits request-scoped audit logs while the Micrometer/OpenTelemetry server span is active so
 * LogstashEncoder can include {@code traceId}/{@code spanId} from MDC.
 */
final class RequestAuditLog {
  private static final Logger LOG = LoggerFactory.getLogger(RequestAuditLog.class);

  private RequestAuditLog() {}

  static void info(String operation, String eventId, String accountId, String outcome) {
    MDC.put("operation", operation);
    MDC.put("eventId", eventId);
    MDC.put("accountId", accountId);
    MDC.put("outcome", outcome);
    try {
      LOG.info("account transaction {}", outcome);
    } finally {
      MDC.remove("operation");
      MDC.remove("eventId");
      MDC.remove("accountId");
      MDC.remove("outcome");
    }
  }
}
