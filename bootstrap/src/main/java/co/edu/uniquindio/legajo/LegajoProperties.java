package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The project's first {@code @ConfigurationProperties}: binds {@code
 * legajo.embedding-provider} and {@code legajo.cors-origins}, both already declared in
 * {@code application.yml} (TRD §14.1) but read by nothing before this feature.
 *
 * <p>{@code embeddingProvider} defaults to {@link EmbeddingProviderMode#CACHED} — matching
 * {@code application.yml}'s own placeholder default ({@code
 * ${LEGAJO_EMBEDDING_PROVIDER:cached}}) — and fails application startup on any other
 * unrecognized string, since {@link EmbeddingProviderMode#fromId} throws; a typo in this
 * property used to be silently ignored, now it fails closed instead.
 *
 * <p>{@code corsOrigins} defaults to an empty list: {@code application.yml}'s own
 * placeholder default ({@code ${LEGAJO_CORS_ORIGINS:}}) resolves to an empty string, and
 * Spring Boot's relaxed binding converts that to zero elements, not one blank element — the
 * CORS configuration built from this list (a separate class) treats an empty list as "no
 * cross-origin request is allowed", never as "allow every origin" (an unset allow-list must
 * never default-open).
 */
@ConfigurationProperties(prefix = "legajo")
public record LegajoProperties(EmbeddingProviderMode embeddingProvider, List<String> corsOrigins) {

    public LegajoProperties {
        embeddingProvider = embeddingProvider == null ? EmbeddingProviderMode.DEFAULT : embeddingProvider;
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }
}
