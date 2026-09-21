package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TRD §14.1/§6.6: {@code legajo.cors-origins} must drive real CORS configuration for the
 * REST API (prefix {@code /api/v1}, TRD §6.6), and an empty value must mean "no
 * cross-origin request is allowed", never "allow every origin". A unit test against the
 * real {@link CorsRegistry} that {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}
 * hands {@code addCorsMappings} — not an end-to-end MockMvc call against
 * {@code /actuator/health} — because actuator endpoints are served by a separate mapping
 * ({@code WebMvcEndpointHandlerMapping}) that never consults {@code WebMvcConfigurer}'s CORS
 * registry at all; the REST controllers this configuration is actually for land in A3/A4.
 */
class CorsWebConfigurationTest {

    /** {@code CorsRegistry#getCorsConfigurations()} is {@code protected}; this subclass, in
     * a different package, can still read it through inheritance. */
    private static final class ReadableCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }

    @Test
    void emptyOriginsRegistersNoCorsMappingAtAll() {
        LegajoProperties properties = new LegajoProperties(EmbeddingProviderMode.CACHED, List.of());
        CorsWebConfiguration configuration = new CorsWebConfiguration(properties);
        ReadableCorsRegistry registry = new ReadableCorsRegistry();

        configuration.addCorsMappings(registry);

        assertThat(registry.configurations()).isEmpty();
    }

    @Test
    void configuredOriginsAreRegisteredExactlyUnderTheApiV1Prefix() {
        LegajoProperties properties = new LegajoProperties(EmbeddingProviderMode.CACHED,
                List.of("http://localhost:5173", "https://legajo.example.com"));
        CorsWebConfiguration configuration = new CorsWebConfiguration(properties);
        ReadableCorsRegistry registry = new ReadableCorsRegistry();

        configuration.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations = registry.configurations();
        assertThat(configurations).containsKey("/api/v1/**");
        assertThat(configurations.get("/api/v1/**").getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://legajo.example.com");
    }
}
