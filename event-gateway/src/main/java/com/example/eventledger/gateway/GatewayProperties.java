package com.example.eventledger.gateway;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "eventledger")
public record GatewayProperties(AccountService accountService, InternalRetry internalRetry, RateLimit rateLimit) {
  public record AccountService(String baseUrl, Duration connectTimeout, Duration responseTimeout) {}
  public record InternalRetry(boolean enabled) {}
  public record RateLimit(boolean enabled, long capacity, long refillTokens, Duration refillPeriod) {}
}
