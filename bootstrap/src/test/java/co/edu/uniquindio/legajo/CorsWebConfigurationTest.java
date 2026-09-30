package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code legajo.cors-origins} must drive real CORS
 * configuration for the REST API (prefix {@code /api/v1}). An empty or absent
 * value falls back to the local development defaults ({@link LegajoProperties} resolves
 * this before {@link CorsWebConfiguration} ever sees the list); a defined list replaces
 * those defaults instead of adding to them; and an empty {@code allowedOrigins} list is
 * never registered, since Spring would then treat it as "allow every origin". A unit test
 * against the
 * real {@link CorsRegistry} that {@link org.springframework.web.servlet.config.annotation.WebMvcConfigurer}
 * hands {@code addCorsMappings} — not an end-to-end MockMvc call against
 * {@code /actuator/health} — because actuator endpoints are served by a separate mapping
 * ({@code WebMvcEndpointHandlerMapping}) that never consults {@code WebMvcConfigurer}'s CORS
 * registry at all; the REST controllers this configuration is actually for are the similarity,
 * clustering, corpus and embeddings controllers.
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
    void emptyOriginsFallBackToTheLocalDefaultsInsteadOfRegisteringAnEmptyMapping() {
        LegajoProperties properties = new LegajoProperties(EmbeddingProviderMode.CACHED, List.of(), null);
        CorsWebConfiguration configuration = new CorsWebConfiguration(properties);
        ReadableCorsRegistry registry = new ReadableCorsRegistry();

        configuration.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations = registry.configurations();
        assertThat(configurations).containsKey("/api/v1/**");
        assertThat(configurations.get("/api/v1/**").getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://localhost");
    }

    @Test
    void configuredOriginsAreRegisteredExactlyUnderTheApiV1Prefix() {
        LegajoProperties properties = new LegajoProperties(EmbeddingProviderMode.CACHED,
                List.of("http://localhost:5173", "https://legajo.example.com"), null);
        CorsWebConfiguration configuration = new CorsWebConfiguration(properties);
        ReadableCorsRegistry registry = new ReadableCorsRegistry();

        configuration.addCorsMappings(registry);

        Map<String, CorsConfiguration> configurations = registry.configurations();
        assertThat(configurations).containsKey("/api/v1/**");
        assertThat(configurations.get("/api/v1/**").getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://legajo.example.com");
    }
}
