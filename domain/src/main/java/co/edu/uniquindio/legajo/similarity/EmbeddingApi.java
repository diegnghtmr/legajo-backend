package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Euclidean distance over cached {@code gemini-embedding-2-preview} unit vectors (TRD §6.3,
 * "Capacidades de embedding (fijadas)"; ADR-015; PRD HU-1.x), hand-written under R-02:
 * {@code d = ‖u−v‖}, {@code normalizedScore = clamp(1 − d/√2, 0, 1)}, {@code rawValue = d}.
 * Identical vectors ({@code d = 0}) map to 1; orthogonal unit vectors ({@code d = √2}) map to
 * 0; a negative cosine ({@code d > √2}) is clamped to 0, using the same [0,1] anchors as the
 * other five capabilities (TRD §6.3). Over L2-normalized vectors, {@code ‖u−v‖² = 2(1 − cos)},
 * so this metric and {@link EmbeddingLocal}'s cosine are monotonically related — this is the
 * second metric the enunciado authorizes ("distancia euclidiana o coseno"), deliberately
 * different from {@code embedding-local}'s.
 *
 * <p>Model inference (the provider's own tokenizer and forward pass) is delegable under R-02
 * and runs entirely offline, before this class ever runs; this class only ever sees the two
 * documents' already-loaded, already-unit-length {@link EmbeddingVector}s through
 * {@link SimilarityInput#embeddingVector()} — the same shape {@link EmbeddingLocal} consumes.
 *
 * <p><b>Degenerate cases.</b> None: like {@code embedding-local}, embedding inputs are never
 * empty (TRD §6.3, "Vector nulo de TF-IDF (fijado)"), so {@code degenerate} is always
 * {@code false} here.
 *
 * <p><b>Provider status (scope note).</b> TRD §6.3's trace row also names "estado del
 * proveedor" and a live-mode 503 when the remote API is down; that behavior belongs to the
 * broader {@code EmbeddingProvider} orchestration (per-pair vector resolution, cached-vs-live
 * choice) that S6a already flagged as an application-layer concern for a later task, not this
 * persistence-and-metric task. {@link EmbeddingApiTrace#providerStatus()} carries the fixed
 * value {@link #PROVIDER_STATUS_CACHED}, matching the current posture where both embedding
 * capabilities are served from their versioned caches in the demo (TRD §5.1 stack table).
 */
public final class EmbeddingApi implements SimilarityAlgorithm {

    /** {@link EmbeddingApiTrace#providerStatus()} value used while only the cached path exists. */
    static final String PROVIDER_STATUS_CACHED = "cached";

    private static final double SQRT_2 = Math.sqrt(2.0);

    @Override
    public String id() {
        return "embedding-api";
    }

    @Override
    public String displayName() {
        return "Embedding (Gemini API)";
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
        double sumSquaredDiff = sumSquaredDifferences(vectorA.values(), vectorB.values());
        double distance = euclideanDistance(sumSquaredDiff);
        double normalizedScore = clamp01(distance);

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(normalizedScore, distance, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        EmbeddingVector vectorA = requireVector(a);
        EmbeddingVector vectorB = requireVector(b);

        double sumSquaredDiff = sumSquaredDifferences(vectorA.values(), vectorB.values());
        double distance = euclideanDistance(sumSquaredDiff);
        double normalizedScore = clamp01(distance);

        AlgorithmTrace trace = new EmbeddingApiTrace(
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
                sumSquaredDiff,
                distance,
                normalizedScore,
                PROVIDER_STATUS_CACHED);
        return Optional.of(trace);
    }

    private static EmbeddingVector requireVector(SimilarityInput input) {
        return Objects.requireNonNull(input.embeddingVector(),
                "embeddingVector is required for embedding-api (TRD §6.3): "
                        + "the caller must attach the cached vector to SimilarityInput before calling compute()/trace()");
    }

    /**
     * Hand-written sum of squared component differences (TRD §3.3: the metric is not
     * delegable) — {@code Σ(u_i − v_i)²}, the value under the square root of the Euclidean
     * distance.
     */
    static double sumSquaredDifferences(List<Double> u, List<Double> v) {
        if (u.size() != v.size()) {
            throw new IllegalArgumentException(
                    "vectors must have the same dimension to compare, was %d and %d".formatted(u.size(), v.size()));
        }
        double sum = 0.0;
        for (int i = 0; i < u.size(); i++) {
            double diff = u.get(i) - v.get(i);
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Hand-written Euclidean distance {@code d = √(sumSquaredDiff)}. Fails closed on a
     * negative or non-finite {@code sumSquaredDiff} instead of silently propagating
     * {@code NaN} through {@code Math.sqrt} of a negative number.
     */
    static double euclideanDistance(double sumSquaredDiff) {
        NumericGuards.requireNonNegativeFinite(sumSquaredDiff, "sumSquaredDiff");
        return Math.sqrt(sumSquaredDiff);
    }

    /**
     * {@code clamp(1 − d/√2, 0, 1)} (TRD §6.3, "Capacidades de embedding (fijadas)"). Fails
     * closed on a non-finite {@code distance} instead of silently propagating it: a bare
     * {@code Math.max}/{@code Math.min} clamp does not reject NaN, it returns NaN, which
     * would otherwise reach callers as a NaN similarity score.
     */
    static double clamp01(double distance) {
        NumericGuards.requireFinite(distance, "distance");
        double normalized = 1.0 - distance / SQRT_2;
        return Math.max(0.0, Math.min(1.0, normalized));
    }

    private static List<Double> excerpt(List<Double> values) {
        return values.subList(0, Math.min(8, values.size()));
    }
}
