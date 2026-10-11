package com.flatmeals.common;

import org.flywaydb.core.Flyway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A tiny endpoint to prove the whole chain works:
 * browser → Spring Boot → MySQL (Flyway has applied our schema).
 *
 * GET /api/health  →  {"status":"OK","schemaVersion":"1"}
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final Flyway flyway;

    // Spring "injects" the Flyway object it created at startup (constructor injection).
    public HealthController(Flyway flyway) {
        this.flyway = flyway;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        var current = flyway.info().current();
        String version = (current == null) ? "none" : current.getVersion().toString();
        return Map.of("status", "OK", "schemaVersion", version);
    }
}
