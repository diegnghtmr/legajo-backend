package co.edu.uniquindio.legajo.similarity;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Cross-cutting, corpus-wide state passed to every {@link SimilarityAlgorithm}
 * invocation, decoupling the sealed contract's shape from state a given capability
 * needs beyond its two inputs — for example the df/N statistics {@code tfidf-cosine}
 * computes once over the whole corpus (TRD §6.3), never per pair.
 *
 * <p>{@code tfIdfIndex} is {@code null} for every capability that needs no shared state
 * (Levenshtein, Needleman-Wunsch, Jaccard), and required only by {@code tfidf-cosine} (S5),
 * which reads it for the corpus-wide {@link TfIdfCorpusIndex#documentFrequency(String)} and
 * {@link TfIdfCorpusIndex#idf(String)}. Later tasks add further fields here the same way,
 * without touching {@code compute(a, b, context)} on {@link SimilarityAlgorithm} or any
 * existing caller.
 */
public record SimilarityContext(@Nullable TfIdfCorpusIndex tfIdfIndex) {

    /** Shared instance for callers that have no corpus-wide state to pass yet. */
    public static final SimilarityContext EMPTY = new SimilarityContext();

    /** Convenience constructor for capabilities that need no corpus-wide state. */
    public SimilarityContext() {
        this(null);
    }

    /** Builds a context carrying the given corpus-wide TF-IDF index. */
    public static SimilarityContext withTfIdfIndex(TfIdfCorpusIndex tfIdfIndex) {
        Objects.requireNonNull(tfIdfIndex, "tfIdfIndex");
        return new SimilarityContext(tfIdfIndex);
    }
}
