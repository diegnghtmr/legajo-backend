package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Euclidean distance over cached {@code gemini-embedding-2-preview} unit vectors, hand-written;
 * no library implements it:
 * {@code d = ‖u−v‖}, {@code normalizedScore = clamp(1 − d/√2, 0, 1)}, {@code rawValue = d}.
 * Identical vectors ({@code d = 0}) map to 1; orthogonal unit vectors ({@code d = √2}) map to
 * 0; a negative cosine ({@code d > √2}) is clamped to 0, using the same [0,1] anchors as the
 * other five capabilities. Over L2-normalized vectors, {@code ‖u−v‖² = 2(1 − cos)},
 * so this metric and {@link EmbeddingLocal}'s cosine are monotonically related — this is the
 * second metric authorized (Euclidean distance or cosine), deliberately
 * different from {@code embedding-local}'s.
 *
 * <p>Model inference (the provider's own tokenizer and forward pass) is delegable
 * and runs entirely offline, before this class ever runs; this class only ever sees the two
 * documents' already-loaded, already-unit-length {@link EmbeddingVector}s through
 * {@link SimilarityInput#embeddingVector()} — the same shape {@link EmbeddingLocal} consumes.
 *
 * <p><b>Degenerate cases.</b> None: like {@code embedding-local}, embedding inputs are never
 * empty (the fixed null-vector convention for the TF-IDF case does not apply here), so
 * {@code degenerate} is always {@code false} here.
 *
 * <p><b>Provider status.</b> The trace row names the provider status. Where the
 * vectors come from (the versioned cache or the live model) is decided outside the
 * domain, at startup, so the status is given to this capability when it is built and
 * reported unchanged in every {@link EmbeddingApiTrace#providerStatus()}. The no-argument
 * constructor keeps {@link #PROVIDER_STATUS_CACHED}, the default demo posture.
 */
public final class EmbeddingApi implements SimilarityAlgorithm {

    /** {@link EmbeddingApiTrace#providerStatus()} value when vectors come from the versioned cache. */
    static final String PROVIDER_STATUS_CACHED = "cached";

    private static final double SQRT_2 = Math.sqrt(2.0);

    private final String providerStatus;

    /** Vectors served from the versioned cache ({@link #PROVIDER_STATUS_CACHED}). */
    public EmbeddingApi() {
        this(PROVIDER_STATUS_CACHED);
    }

    /** @param providerStatus where the vectors come from, e.g. {@code "cached"} or {@code "live"} */
    public EmbeddingApi(String providerStatus) {
        Objects.requireNonNull(providerStatus, "providerStatus");
        if (providerStatus.isBlank()) {
            throw new IllegalArgumentException("providerStatus must not be blank");
        }
        this.providerStatus = providerStatus;
    }

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
                providerStatus);
        return Optional.of(trace);
    }

    private static EmbeddingVector requireVector(SimilarityInput input) {
        return Objects.requireNonNull(input.embeddingVector(),
                "embeddingVector is required for embedding-api: "
                        + "the caller must attach the cached vector to SimilarityInput before calling compute()/trace()");
    }

    /**
     * Hand-written sum of squared component differences — no library implements it, the
     * metric is not delegable — {@code Σ(u_i − v_i)²}, the value under the square root of
     * the Euclidean distance.
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
     * {@code clamp(1 − d/√2, 0, 1)}, the fixed embedding-score mapping. Fails
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
