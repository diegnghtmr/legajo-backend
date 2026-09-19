package co.edu.uniquindio.legajo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Composition root. Wires {@code :domain}, {@code :application}, and {@code :infrastructure}
 * together; no business logic lives here (TRD §4.3).
 */
@SpringBootApplication
public class LegajoApplication {

    public static void main(String[] args) {
        SpringApplication.run(LegajoApplication.class, args);
    }
}
