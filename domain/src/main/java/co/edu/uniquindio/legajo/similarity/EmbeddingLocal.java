package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Cosine similarity over cached MiniLM unit vectors, hand-written; no library implements
 * it: the dot product of two unit vectors is
 * their cosine directly (no separate division by norms is needed, since both are already
 * length 1 — the fixed unit-norm invariant); {@code normalizedScore = clamp(cos,
 * 0, 1)} — a negative cosine (theoretically possible on dense vectors) is reported as
 * similarity 0, and the upper clamp absorbs floating-point overshoot on self-comparisons;
 * {@code rawValue = cos} keeps the unclamped sign. Model inference and its tokenizer are
 * delegable and run entirely offline, before this class ever runs (subject to MiniLM's
 * token limit); this class only ever sees the two documents' already-loaded,
 * already-unit-length {@link EmbeddingVector}s through {@link SimilarityInput#embeddingVector()}.
 *
 * <p><b>Degenerate cases.</b> None: unlike {@code tfidf-cosine}, embedding inputs are never
 * empty (the fixed null-vector convention for the TF-IDF case does not apply here: the raw
 * abstract that feeds precompute is guaranteed non-empty by {@code verify-corpus}), so
 * {@code degenerate} is always {@code false} here.
 */
public final class EmbeddingLocal implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "embedding-local";
    }

    @Override
    public String displayName() {
        return "Embedding (MiniLM local)";
    }

    @Override
    public AlgorithmKind kind() {
        return AlgorithmKind.AI;
    }

    @Override
    public SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        long start = System.nanoTime();

        EmbeddingVector vectorA = requireVector(a);
        EmbeddingVector vectorB = requireVector(b);
        double cosine = dotProduct(vectorA.values(), vectorB.values());
        double normalizedScore = clamp01(cosine);

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(normalizedScore, cosine, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        EmbeddingVector vectorA = requireVector(a);
        EmbeddingVector vectorB = requireVector(b);

        double dotProduct = dotProduct(vectorA.values(), vectorB.values());
        double cosine = dotProduct;
        double normalizedScore = clamp01(cosine);
        double angleDegrees = angleDegrees(cosine);

        AlgorithmTrace trace = new EmbeddingLocalTrace(
                id(),
                vectorA.provider(),
                vectorA.model(),
                vectorA.dimension(),
                excerpt(vectorA.values()),
                excerpt(vectorB.values()),
                vectorA.values(),
                vectorB.values(),
                vectorA.preNormL2(),
                vectorB.preNormL2(),
                dotProduct,
                cosine,
                angleDegrees,
                normalizedScore);
        return Optional.of(trace);
    }

    private static EmbeddingVector requireVector(SimilarityInput input) {
        return Objects.requireNonNull(input.embeddingVector(),
                "embeddingVector is required for embedding-local: "
                        + "the caller must attach the cached vector to SimilarityInput before calling compute()/trace()");
    }

    /**
     * Hand-written dot product (no library implements it: the metric is not delegable). For two unit
     * vectors, this value is exactly the cosine of the angle between them.
     */
    static double dotProduct(List<Double> u, List<Double> v) {
        if (u.size() != v.size()) {
            throw new IllegalArgumentException(
                    "vectors must have the same dimension to compare, was %d and %d".formatted(u.size(), v.size()));
        }
        double sum = 0.0;
        for (int i = 0; i < u.size(); i++) {
            sum += u.get(i) * v.get(i);
        }
        return sum;
    }

    /**
     * {@code clamp(cos, 0, 1)}, the fixed embedding-score mapping. Fails
     * closed on a non-finite {@code cosine} instead of silently propagating it: a bare
     * {@code Math.max}/{@code Math.min} clamp does not reject NaN, it returns NaN, which
     * would otherwise reach callers as a NaN similarity score.
     */
    static double clamp01(double cosine) {
        NumericGuards.requireFinite(cosine, "cosine");
        return Math.max(0.0, Math.min(1.0, cosine));
    }

    /** {@code degrees(acos(clamp(cos, -1, 1)))}, hand-written. */
    static double angleDegrees(double cosine) {
        double clamped = Math.max(-1.0, Math.min(1.0, cosine));
        return Math.toDegrees(Math.acos(clamped));
    }

    private static List<Double> excerpt(List<Double> values) {
        return values.subList(0, Math.min(8, values.size()));
    }
}
