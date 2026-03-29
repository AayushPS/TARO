package org.Aayush.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Stage F1 application bootstrap for the TARO HTTP/API surface.
 * Satisfies closure criterion: frontend retrieval flow can inspect retained future-aware results without recomputing them.
 */
@SpringBootApplication(scanBasePackages = "org.Aayush")
public class Main {
    /**
     * Stage F1 launches the Spring Boot API layer.
     * Satisfies closure criterion: API endpoints for retained-result inspection are available from one canonical entrypoint.
     *
     * @param args command-line arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }
}
