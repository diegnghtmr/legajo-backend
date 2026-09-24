package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * The project's first {@code @ConfigurationProperties}: binds {@code
 * legajo.embedding-provider} and {@code legajo.cors-origins}, both already declared in
 * {@code application.yml} but read by nothing before this feature.
 *
 * <p>{@code embeddingProvider} defaults to {@link EmbeddingProviderMode#CACHED} — matching
 * {@code application.yml}'s own placeholder default ({@code
 * ${LEGAJO_EMBEDDING_PROVIDER:cached}}) — and fails application startup on any other
 * unrecognized string, since {@link EmbeddingProviderMode#fromId} throws; a typo in this
 * property used to be silently ignored, now it fails closed instead.
 *
 * <p>{@code corsOrigins}: {@code application.yml}'s own placeholder
 * default ({@code ${LEGAJO_CORS_ORIGINS:}}) resolves to an empty string, and Spring Boot's
 * relaxed binding converts that to zero elements, not one blank element. When the bound list
 * is null, empty, or holds only blank entries, this record substitutes {@link
 * #DEFAULT_CORS_ORIGINS} — the local development origins {@code http://localhost:5173}
 * (Vite dev server) and {@code http://localhost} (Compose frontend on :80) — never an empty
 * list; {@code LEGAJO_CORS_ORIGINS} cannot default to the Vercel origin because that origin
 * is not known until deploy time, so it is set explicitly in Render instead. A defined,
 * non-blank list <b>replaces</b> those defaults; it is never merged with them. {@link
 * CorsWebConfiguration} therefore never has to invent a default itself, and can keep its own
 * defensive rule of never registering an empty {@code allowedOrigins} list, which Spring
 * would otherwise treat as "allow every origin".
 */
@ConfigurationProperties(prefix = "legajo")
public record LegajoProperties(EmbeddingProviderMode embeddingProvider, List<String> corsOrigins) {

    /** Origins allowed when {@code LEGAJO_CORS_ORIGINS} is empty or absent. */
    public static final List<String> DEFAULT_CORS_ORIGINS = List.of("http://localhost:5173", "http://localhost");

    public LegajoProperties {
        embeddingProvider = embeddingProvider == null ? EmbeddingProviderMode.DEFAULT : embeddingProvider;
        corsOrigins = normalizeCorsOrigins(corsOrigins);
    }

    private static List<String> normalizeCorsOrigins(List<String> corsOrigins) {
        List<String> nonBlank = corsOrigins == null
                ? List.of()
                : corsOrigins.stream().filter(origin -> origin != null && !origin.isBlank()).toList();
        return nonBlank.isEmpty() ? DEFAULT_CORS_ORIGINS : nonBlank;
    }
}
