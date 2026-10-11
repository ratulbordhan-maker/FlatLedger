package com.flatmeals.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyCheckTest {

    private static final String GOOD_SECRET = "k3N9vQ2xR7mL0pZ8sT4wY6bH1cJ5dF0gA2eU9iO3";

    @Test
    void acceptsRealSettings() {
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "a-long-flat-password")).isEmpty();
    }

    @Test
    void refusesTheExampleJwtSecret() {
        assertThat(ProductionSafetyCheck.problems("dev-only-secret-change-me-before-deploying-0123456789", "a-long-flat-password"))
                .singleElement().asString().contains("JWT_SECRET");
    }

    @Test
    void refusesTheExampleOrAShortManagerPassword() {
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "changeme123")).hasSize(1);
        assertThat(ProductionSafetyCheck.problems(GOOD_SECRET, "short")).hasSize(1);
    }

    @Test
    void stopsTheAppFromStarting() {
        assertThatThrownBy(() -> new ProductionSafetyCheck("dev-only-secret-change-me-before-deploying-0123456789", "changeme123"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("MANAGER_PASSWORD");
    }
}
