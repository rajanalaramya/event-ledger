package com.example.eventledger.gateway;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfiguration {
  @Bean OpenAPI gatewayOpenApi() {
    return new OpenAPI().info(new Info().title("Event Ledger Gateway").version("v1"));
  }
  @Bean Filter securityHeadersFilter() {
    return new Filter() {
      @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
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
