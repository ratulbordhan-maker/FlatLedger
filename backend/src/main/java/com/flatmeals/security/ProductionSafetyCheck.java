package com.flatmeals.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs only on the live server (profile "prod") and stops the app from
 * starting with the test settings that are fine on a laptop but dangerous online:
 *
 *  - the example JWT secret from application.yml (anyone who reads the code
 *    on GitHub could then make their own manager token);
 *  - the example first-manager password "changeme123", or any password shorter than 8 characters.
 *
 * Failing loudly at startup is much better than running unsafely without noticing.
 */
@Component
@Profile("prod")
public class ProductionSafetyCheck {

    static final String EXAMPLE_PASSWORD = "changeme123";

    public ProductionSafetyCheck(@Value("${app.jwt.secret}") String jwtSecret,
                                 @Value("${app.bootstrap.manager-password}") String managerPassword) {
        List<String> problems = problems(jwtSecret, managerPassword);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Refusing to start in production: " + String.join(" ", problems));
        }
    }

    static List<String> problems(String jwtSecret, String managerPassword) {
        List<String> problems = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.contains("dev-only") || jwtSecret.length() < 32) {
            problems.add("Set JWT_SECRET to your own long random value (at least 32 characters).");
        }
        if (managerPassword == null || managerPassword.length() < 8 || managerPassword.equals(EXAMPLE_PASSWORD)) {
            problems.add("Set MANAGER_PASSWORD to your own password of at least 8 characters (not changeme123).");
        }
        return problems;
    }
}
