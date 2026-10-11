package com.flatmeals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the backend. Running this class starts the web server on port 8080.
 * Spring scans this package (com.flatmeals) and every sub-package for controllers,
 * services and repositories, so all feature packages must live under it.
 */
@SpringBootApplication
public class FlatMealsApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlatMealsApplication.class, args);
    }
}
