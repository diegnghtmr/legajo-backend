package co.edu.uniquindio.legajo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes {@code legajo.cors-origins} genuinely drive CORS (TRD §14.1/§14.3), the second of
 * the two dead configuration keys this feature makes live.
 *
 * <p><b>Defaults and replacement live in {@link LegajoProperties} (TRD §14.4, 1.3.8).</b>
 * {@link LegajoProperties#corsOrigins()} already resolves an empty or absent
 * {@code LEGAJO_CORS_ORIGINS} to the local development origins, and a defined list already
 * replaces those defaults rather than adding to them — this class only registers whatever
 * list it is handed. The {@code isEmpty()} guard below is defense in depth, not the normal
 * path: {@link LegajoProperties} should never actually hand this class an empty list, but if
 * it somehow did, registering a mapping with an empty {@code allowedOrigins} array would be
 * the opposite of closed — {@code CorsConfiguration}'s own default-value logic treats "no
 * origin explicitly configured" as "allow every origin" ({@code applyPermitDefaultValues()}) —
 * so this configurer registers no mapping at all instead.
 */
@Configuration
@EnableConfigurationProperties(LegajoProperties.class)
public class CorsWebConfiguration implements WebMvcConfigurer {

    private final LegajoProperties properties;

    public CorsWebConfiguration(LegajoProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (properties.corsOrigins().isEmpty()) {
            return;
        }
        registry.addMapping("/api/v1/**")
                .allowedOrigins(properties.corsOrigins().toArray(new String[0]))
                .allowedMethods("GET", "POST");
    }
}
