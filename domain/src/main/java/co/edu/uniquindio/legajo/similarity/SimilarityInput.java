package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * One side of a pairwise comparison, carrying both text representations a
 * {@link SimilarityAlgorithm} might need (TRD §6.3, "Texto de entrada por familia"), plus the
 * precomputed embedding vector the two embedding-based capabilities read instead of
 * re-running model inference at compare time:
 *
 * <ul>
 *   <li>{@code tokens} — the five-step preprocessed stream (TRD §6.2) read by the four
 *       classical capabilities and by {@code tfidf-cosine};</li>
 *   <li>{@code rawAbstract} — the cleaned-but-unprocessed abstract text that fed the
 *       <em>offline</em> embedding precompute for {@code embedding-local} and
 *       {@code embedding-api}, because each pretrained model owns its own tokenizer
 *       (delegable under R-02) and must not be fed the stopword-stripped, tokenized stream
 *       meant for the hand-written algorithms;</li>
 *   <li>{@code embeddingVector} — the already-loaded, unit-length {@link EmbeddingVector}
 *       for this document (TRD §6.3, "Invariante de norma unitaria (fijado)"), {@code null}
 *       for every capability that does not need one (Levenshtein, Needleman-Wunsch, Jaccard,
 *       {@code tfidf-cosine}). Whichever caller resolves the corpus's cached vectors (an
 *       application-layer concern, mirroring how {@code tokens} is already resolved before
 *       reaching this record) attaches it here; {@code embedding-local}'s {@code compute}
 *       never reads the embedding cache itself.</li>
 * </ul>
 *
 * <p>Bundling every representation in a single record — rather than giving classical and
 * embedding algorithms different {@code compute} signatures — keeps
 * {@link SimilarityAlgorithm} a single flat sealed interface whose one
 * {@code compute(a, b, context)} shape can host all six capabilities (present and future)
 * without the registry or its caller branching on {@code kind()} before every call.
 */
public record SimilarityInput(String rawAbstract, List<String> tokens, @Nullable EmbeddingVector embeddingVector) {

    public SimilarityInput {
        Objects.requireNonNull(rawAbstract, "rawAbstract");
        Objects.requireNonNull(tokens, "tokens");
        tokens = List.copyOf(tokens);
    }

    /** Convenience constructor for capabilities that need no precomputed embedding vector. */
    public SimilarityInput(String rawAbstract, List<String> tokens) {
        this(rawAbstract, tokens, null);
    }
}
