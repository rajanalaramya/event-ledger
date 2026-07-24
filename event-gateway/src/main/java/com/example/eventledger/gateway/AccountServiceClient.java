package com.example.eventledger.gateway;

import com.example.eventledger.contract.v1.AccountTransactionRequest;
import com.example.eventledger.contract.v1.AccountTransactionResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountServiceClient {
  private final RestClient client;
  private final Bulkhead bulkhead;
  private final CircuitBreaker circuitBreaker;
  private final Retry retry;

  public AccountServiceClient(
      GatewayProperties properties,
      ObservationRegistry observationRegistry,
      BulkheadRegistry bulkheadRegistry,
      CircuitBreakerRegistry circuitBreakerRegistry,
      RetryRegistry retryRegistry) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.accountService().connectTimeout());
    factory.setReadTimeout(properties.accountService().responseTimeout());
    // ObservationRegistry enables Micrometer Tracing to inject W3C traceparent on outbound calls.
    client =
        RestClient.builder()
            .baseUrl(properties.accountService().baseUrl())
            .requestFactory(factory)
            .observationRegistry(observationRegistry)
            .build();
    bulkhead = bulkheadRegistry.bulkhead("accountService");
    circuitBreaker = circuitBreakerRegistry.circuitBreaker("accountService");
    retry = retryRegistry.retry("accountService");
  }

  public AccountTransactionResponse apply(AccountTransactionRequest request) {
    Supplier<AccountTransactionResponse> protectedCall =
        Bulkhead.decorateSupplier(
            bulkhead,
            CircuitBreaker.decorateSupplier(
                circuitBreaker,
                Retry.decorateSupplier(
                    retry,
                    () ->
                        client
                            .post()
                            .uri("/accounts/{accountId}/transactions", request.accountId())
                            .body(request)
                            .retrieve()
                            .body(AccountTransactionResponse.class))));
    return protectedCall.get();
  }

  public void health() {
    client.get().uri("/actuator/health").retrieve().toBodilessEntity();
  }
}
