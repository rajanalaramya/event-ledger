package com.example.eventledger.gateway;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness reports this contributor. The public /health endpoint intentionally reports process
 * liveness only, so an unavailable downstream does not obscure whether this JVM is running.
 */
@Component("accountService")
public class AccountServiceHealthIndicator implements HealthIndicator {
  private final AccountServiceClient client;
  public AccountServiceHealthIndicator(AccountServiceClient client) { this.client = client; }
  @Override public Health health() {
    try {
      client.health();
      return Health.up().build();
    } catch (Exception e) { return Health.down(e).build(); }
  }
}
