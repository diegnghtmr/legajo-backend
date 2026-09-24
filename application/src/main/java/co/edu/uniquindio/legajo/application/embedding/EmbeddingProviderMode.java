package co.edu.uniquindio.legajo.application.embedding;

import java.util.Objects;

/**
 * The two runtime modes {@code legajo.embedding-provider} selects: {@code
 * cached} — the default and only mode the default profile needs, serving vectors from the
 * versioned {@code embeddings-*.json} caches with no network dependency — and {@code live},
 * which additionally requires the four {@code SPRING_AI_OPENAI_*}/{@code
 * LEGAJO_EMBEDDING_API_*} variables and degrades to a 503 Problem Detail on failure.
 * Building or wiring the live embedding-refresh path itself is out of
 * scope here (left to the REST adapters); this type only gives the
 * property a validated, typed value instead of an unread raw string.
 *
 * <p>Mirrors {@link co.edu.uniquindio.legajo.similarity.Representation}'s {@code id()}/
 * {@code fromId(String)} convention so the property's raw string round-trips the same way a
 * REST/CLI parameter would.
 */
public enum EmbeddingProviderMode {

    CACHED("cached"),
    LIVE("live");

    /** The default profile operates with {@code LEGAJO_EMBEDDING_PROVIDER=cached}. */
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
