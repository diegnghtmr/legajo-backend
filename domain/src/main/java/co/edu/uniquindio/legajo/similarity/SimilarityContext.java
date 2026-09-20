package co.edu.uniquindio.legajo.similarity;

/**
 * Cross-cutting, corpus-wide state passed to every {@link SimilarityAlgorithm}
 * invocation, decoupling the sealed contract's shape from state a given capability
 * needs beyond its two inputs — for example the df/N statistics {@code tfidf-cosine}
 * computes once over the whole corpus (TRD §6.3), never per pair.
 *
 * <p>Intentionally empty in S1–S2: Levenshtein needs no shared state. Later tasks add
 * the fields their capability requires here, without touching
 * {@code compute(a, b, context)} on {@link SimilarityAlgorithm} or any existing caller.
 */
public record SimilarityContext() {

    /** Shared instance for callers that have no corpus-wide state to pass yet. */
    public static final SimilarityContext EMPTY = new SimilarityContext();
}
