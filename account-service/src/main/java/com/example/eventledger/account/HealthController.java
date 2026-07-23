package com.example.eventledger.account;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
  private final DataSource dataSource;

  public HealthController(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("status", "UP");
    body.put("service", "account-service");
    body.put("version", "1.0.0-SNAPSHOT");
    body.put("database", databaseStatus());
    return body;
  }

  private Map<String, Object> databaseStatus() {
    Map<String, Object> db = new LinkedHashMap<>();
    try (Connection connection = dataSource.getConnection()) {
      boolean valid = connection.isValid(1);
      db.put("status", valid ? "UP" : "DOWN");
      db.put("product", connection.getMetaData().getDatabaseProductName());
    } catch (Exception ex) {
      db.put("status", "DOWN");
      db.put("error", "database unreachable");
    }
    return db;
  }
}
