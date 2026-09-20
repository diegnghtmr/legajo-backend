package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;

/**
 * One side of a pairwise comparison, carrying both text representations a
 * {@link SimilarityAlgorithm} might need (TRD §6.3, "Texto de entrada por familia"):
 *
 * <ul>
 *   <li>{@code tokens} — the five-step preprocessed stream (TRD §6.2) read by the four
 *       classical capabilities and by {@code tfidf-cosine};</li>
 *   <li>{@code rawAbstract} — the cleaned-but-unprocessed abstract text read by
 *       {@code embedding-local} and {@code embedding-api}, because each pretrained model
 *       owns its own tokenizer (delegable under R-02) and must not be fed the
 *       stopword-stripped, tokenized stream meant for the hand-written algorithms.</li>
 * </ul>
 *
 * <p>Bundling both representations in a single record — rather than giving classical and
 * embedding algorithms two different {@code compute} signatures — keeps
 * {@link SimilarityAlgorithm} a single flat sealed interface whose one
 * {@code compute(a, b, context)} shape can host all six capabilities (present and future)
 * without the registry or its caller branching on {@code kind()} before every call.
 */
public record SimilarityInput(String rawAbstract, List<String> tokens) {

    public SimilarityInput {
        Objects.requireNonNull(rawAbstract, "rawAbstract");
        Objects.requireNonNull(tokens, "tokens");
        tokens = List.copyOf(tokens);
    }
}
