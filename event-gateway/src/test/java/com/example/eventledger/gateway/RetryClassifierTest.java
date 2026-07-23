package com.example.eventledger.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class RetryClassifierTest {
  @Test void retriesOnlyTransientFailures() {
    assertThat(RetryClassifier.isRetryable(new IOException("down"))).isTrue();
    assertThat(RetryClassifier.isRetryable(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "", HttpHeaders.EMPTY, null, null))).isTrue();
    assertThat(RetryClassifier.isRetryable(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "", HttpHeaders.EMPTY, null, null))).isFalse();
    assertThat(RetryClassifier.isRetryable(HttpClientErrorException.create(HttpStatus.CONFLICT, "", HttpHeaders.EMPTY, null, null))).isFalse();
  }
}
