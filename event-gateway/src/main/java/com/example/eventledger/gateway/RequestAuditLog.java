package com.example.eventledger.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Request-scoped audit log while the server span / MDC tracing context is active. */
final class RequestAuditLog {
  private static final Logger LOG = LoggerFactory.getLogger(RequestAuditLog.class);

  private RequestAuditLog() {}

  static void info(String operation, String eventId, String accountId, String outcome) {
    MDC.put("operation", operation);
    MDC.put("eventId", eventId);
    MDC.put("accountId", accountId);
    MDC.put("outcome", outcome);
    try {
      LOG.info("gateway event {}", outcome);
    } finally {
      MDC.remove("operation");
      MDC.remove("eventId");
      MDC.remove("accountId");
      MDC.remove("outcome");
    }
  }
}
