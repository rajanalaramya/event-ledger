package com.example.eventledger.gateway;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class RateLimitFilter implements Filter {
  private final GatewayProperties properties;
  private final Bucket bucket;
  public RateLimitFilter(GatewayProperties properties) {
    this.properties = properties;
    bucket = Bucket.builder().addLimit(Bandwidth.classic(properties.rateLimit().capacity(),
        Refill.greedy(properties.rateLimit().refillTokens(), properties.rateLimit().refillPeriod()))).build();
  }
  @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    if (properties.rateLimit().enabled() && "POST".equals(httpRequest.getMethod()) && "/events".equals(httpRequest.getRequestURI())
        && !bucket.tryConsume(1)) {
      HttpServletResponse httpResponse = (HttpServletResponse) response;
      httpResponse.setStatus(429); httpResponse.setHeader("Retry-After", "1"); httpResponse.setContentType("application/problem+json");
      httpResponse.getWriter().write("{\"title\":\"Rate limit exceeded\",\"status\":429,\"errorCode\":\"RATE_LIMITED\"}"); return;
    }
    chain.doFilter(request, response);
  }
}
