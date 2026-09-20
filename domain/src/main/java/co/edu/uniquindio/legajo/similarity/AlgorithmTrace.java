package co.edu.uniquindio.legajo.similarity;

/**
 * Marker for the step-by-step evidence a {@link SimilarityAlgorithm} can expose through
 * {@code trace(a, b, context)} (TRD §6.3): a human/UI-auditable record of how a result was
 * computed, distinct from the numeric {@link SimilarityResult}. {@link DpMatrixTrace} is
 * shared by Levenshtein (S2) and Needleman–Wunsch (S3); {@link JaccardTrace} (S4) is the
 * set-based trace for Jaccard; {@link TfIdfCosineTrace} (S5) is the term-by-term trace for
 * TF-IDF cosine; {@link EmbeddingLocalTrace} (S6) is the vector-based trace for
 * {@code embedding-local}; {@link EmbeddingApiTrace} (S6b) is the Euclidean-distance trace
 * for {@code embedding-api}, the sixth and last permitted record TRD §6.3 requires.
 */
public sealed interface AlgorithmTrace
        permits DpMatrixTrace, JaccardTrace, TfIdfCosineTrace, EmbeddingLocalTrace, EmbeddingApiTrace {
}
