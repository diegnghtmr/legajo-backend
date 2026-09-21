package co.edu.uniquindio.legajo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Makes {@code legajo.cors-origins} genuinely drive CORS (TRD §14.1/§14.3), the second of
 * the two dead configuration keys this feature makes live.
 *
 * <p><b>Empty means closed, not open (fixed by the feature document).</b> When {@link
 * LegajoProperties#corsOrigins()} is empty, this configurer registers no CORS mapping at
 * all, so Spring MVC applies no CORS headers and browsers block the cross-origin request by
 * their own same-origin policy. Registering a mapping with an empty {@code allowedOrigins}
 * array instead would be the opposite of "closed": {@code CorsConfiguration}'s own default-
 * value logic treats "no origin explicitly configured" as "allow every origin"
 * ({@code applyPermitDefaultValues()}), which is exactly the accidental wide-open
 * configuration this property must not produce when unset.
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
