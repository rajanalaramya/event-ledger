package com.example.eventledger.gateway;

import java.io.IOException;
import java.net.SocketTimeoutException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

public final class RetryClassifier {
  private RetryClassifier() {}
  public static boolean isRetryable(Throwable error) {
    if (error instanceof ResourceAccessException || error instanceof IOException || error instanceof SocketTimeoutException) return true;
    if (error instanceof HttpServerErrorException server) {
      int status = server.getStatusCode().value();
      return status == 502 || status == 503 || status == 504;
    }
    return false;
  }
}
