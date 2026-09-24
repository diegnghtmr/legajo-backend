package co.edu.uniquindio.legajo.similarity;

/**
 * Marker for the step-by-step evidence a {@link SimilarityAlgorithm} can expose through
 * {@code trace(a, b, context)}: a human/UI-auditable record of how a result was
 * computed, distinct from the numeric {@link SimilarityResult}. {@link DpMatrixTrace} is
 * shared by Levenshtein and Needleman–Wunsch; {@link JaccardTrace} is the
 * set-based trace for Jaccard; {@link TfIdfCosineTrace} is the term-by-term trace for
 * TF-IDF cosine; {@link EmbeddingLocalTrace} is the vector-based trace for
 * {@code embedding-local}; {@link EmbeddingApiTrace} is the Euclidean-distance trace
 * for {@code embedding-api}, the sixth and last permitted record required.
 */
public sealed interface AlgorithmTrace
        permits DpMatrixTrace, JaccardTrace, TfIdfCosineTrace, EmbeddingLocalTrace, EmbeddingApiTrace {
}
