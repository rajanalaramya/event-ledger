package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.AccountTransactionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AccountServiceClient {
  private final RestClient client;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public AccountServiceClient(GatewayProperties properties, ObservationRegistry observationRegistry,
      CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.accountService().connectTimeout());
    factory.setReadTimeout(properties.accountService().responseTimeout());
    client = RestClient.builder().baseUrl(properties.accountService().baseUrl())
        .requestFactory(factory).observationRegistry(observationRegistry).build();
    circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
    retry = retryRegistry.retry("accountService");
  }

  public AccountTransactionResponse apply(AccountTransactionRequest request) {
    Supplier<AccountTransactionResponse> protectedCall = CircuitBreaker.decorateSupplier(circuitBreaker,
        Retry.decorateSupplier(retry, () -> {
          RestClient.RequestHeadersSpec<?> call = client.post()
              .uri("/accounts/{accountId}/transactions", request.accountId()).body(request);
          String traceparent = incomingTraceparent();
          if (traceparent != null) call.header("traceparent", traceparent);
          return call.retrieve().body(AccountTransactionResponse.class);
        }));
    return protectedCall.get();
  }

  public void health() {
    client.get().uri("/actuator/health").retrieve().toBodilessEntity();
  }

  private String incomingTraceparent() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
      return attributes.getRequest().getHeader("traceparent");
    }
    return null;
  }
}
