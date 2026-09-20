package co.edu.uniquindio.legajo.similarity;

/**
 * Marker for the step-by-step evidence a {@link SimilarityAlgorithm} can expose through
 * {@code trace(a, b, context)} (TRD §6.3): a human/UI-auditable record of how a result was
 * computed, distinct from the numeric {@link SimilarityResult}. {@link DpMatrixTrace} is
 * the only permitted type today, shared by Levenshtein (S2) and Needleman–Wunsch (S3);
 * later tasks add the Jaccard set trace, the TF-IDF term-by-term trace, and the two
 * embedding-vector traces as new permitted records, without changing this marker or any
 * existing caller.
 */
public sealed interface AlgorithmTrace permits DpMatrixTrace {
}
