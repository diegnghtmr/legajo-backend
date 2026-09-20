package co.edu.uniquindio.legajo.similarity;

/**
 * The two families of {@link SimilarityAlgorithm} the TRD §6.3 contract distinguishes:
 * {@code CLASSIC} for the four hand-written algebraic/DP algorithms plus
 * {@code tfidf-cosine}, and {@code AI} for the two embedding-based capabilities. The
 * registry and future REST/UI layers group and label capabilities by this field without
 * inspecting the concrete permitted type.
 */
public enum AlgorithmKind {
    CLASSIC,
    AI
}
