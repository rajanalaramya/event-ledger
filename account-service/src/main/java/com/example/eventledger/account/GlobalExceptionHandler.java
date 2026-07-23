package com.example.eventledger.account;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ConflictException.class)
  ProblemDetail conflict(ConflictException exception, HttpServletRequest request) {
    return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage(), "CONFLICT", request);
  }

  @ExceptionHandler({IllegalArgumentException.class})
  ProblemDetail validation(IllegalArgumentException exception, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage(), "VALIDATION_ERROR", request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
    return problem(HttpStatus.BAD_REQUEST, "Validation failed", "request body is invalid", "VALIDATION_ERROR", request);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException exception, HttpServletRequest request) {
    return problem(HttpStatus.valueOf(exception.getStatusCode().value()), "Request failed",
        Optional.ofNullable(exception.getReason()).orElse("Request failed"), "NOT_FOUND", request);
  }

  private ProblemDetail problem(HttpStatus status, String title, String detail, String code, HttpServletRequest request) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setInstance(java.net.URI.create(request.getRequestURI()));
    problem.setProperty("errorCode", code);
    problem.setProperty("traceId", Optional.ofNullable(MDC.get("traceId")).orElse(""));
    problem.setProperty("timestamp", Instant.now().toString());
    return problem;
  }
}
