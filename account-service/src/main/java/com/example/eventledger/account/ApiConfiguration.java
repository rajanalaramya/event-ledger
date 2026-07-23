package com.example.eventledger.account;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiConfiguration {
  @Bean
  OpenAPI accountOpenApi() {
    return new OpenAPI().info(new Info().title("Event Ledger Account Service").version("v1"));
  }

  @Bean
  Filter securityHeadersFilter() {
    return new Filter() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
          throws IOException, ServletException {
        HttpServletResponse http = (HttpServletResponse) response;
        http.setHeader("X-Content-Type-Options", "nosniff");
        http.setHeader("X-Frame-Options", "DENY");
        http.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
      }
    };
  }
}
