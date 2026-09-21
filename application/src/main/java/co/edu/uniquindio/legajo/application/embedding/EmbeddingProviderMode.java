package co.edu.uniquindio.legajo.application.embedding;

import java.util.Objects;

/**
 * The two runtime modes {@code legajo.embedding-provider} selects (TRD §14.1): {@code
 * cached} — the default and only mode the default profile needs, serving vectors from the
 * versioned {@code embeddings-*.json} caches with no network dependency — and {@code live},
 * which additionally requires the four {@code SPRING_AI_OPENAI_*}/{@code
 * LEGAJO_EMBEDDING_API_*} variables and degrades to a 503 Problem Detail on failure
 * (NFR-QA-12). Building or wiring the live embedding-refresh path itself is out of this
 * feature's A1/A2 scope (left to the REST adapters, A3/A4); this type only gives the
 * property a validated, typed value instead of the unread raw string it was before this
 * feature.
 *
 * <p>Mirrors {@link co.edu.uniquindio.legajo.similarity.Representation}'s {@code id()}/
 * {@code fromId(String)} convention so the property's raw string round-trips the same way a
 * REST/CLI parameter would.
 */
public enum EmbeddingProviderMode {

    CACHED("cached"),
    LIVE("live");

    /** TRD §14.1: "El perfil por defecto ... opera con LEGAJO_EMBEDDING_PROVIDER=cached". */
    public static final EmbeddingProviderMode DEFAULT = CACHED;

    private final String id;

    EmbeddingProviderMode(String id) {
        this.id = id;
    }

    /** Stable, hyphenated identifier, e.g. {@code "cached"}. */
    public String id() {
        return id;
    }

    /** Finds the mode whose {@link #id()} equals {@code id}, or throws if none matches. */
    public static EmbeddingProviderMode fromId(String id) {
        Objects.requireNonNull(id, "id");
        for (EmbeddingProviderMode mode : values()) {
            if (mode.id.equals(id)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("no embedding provider mode with id: " + id);
    }
}
