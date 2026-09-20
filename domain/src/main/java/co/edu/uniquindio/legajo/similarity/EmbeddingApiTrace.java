package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;

/**
 * Step-by-step evidence for {@code embedding-api} (TRD §6.3's embedding-api trace row): each
 * vector's first 8 dimensions and full dimension count, the renormalization to unit length
 * applied on load ({@code preNormL2} provenance for each vector, TRD §6.3, "Invariante de
 * norma unitaria (fijado)"), the sum of squared component differences, the Euclidean
 * distance, the {@code clamp(1 − d/√2, 0, 1)} mapping applied, and the provider status.
 *
 * <p>Validated invariants: {@code vectorA}/{@code vectorB} have size {@code dimension};
 * {@code vectorAExcerpt}/{@code vectorBExcerpt} are exactly the first
 * {@code min(8, dimension)} components of the corresponding full vector; {@code sumSquaredDiff}
 * equals the hand-computed {@code Σ(u_i − v_i)²} of {@code vectorA} and {@code vectorB};
 * {@code distance} equals {@code √(sumSquaredDiff)}; {@code normalizedScore} equals
 * {@code clamp(1 − distance/√2, 0, 1)} (TRD §6.3, "Capacidades de embedding (fijadas)").
 */
public record EmbeddingApiTrace(
        String algorithmId,
        String provider,
        String model,
        int dimension,
        List<Double> vectorAExcerpt,
        List<Double> vectorBExcerpt,
        List<Double> vectorA,
        List<Double> vectorB,
        double preNormL2A,
        double preNormL2B,
        double sumSquaredDiff,
        double distance,
        double normalizedScore,
        String providerStatus) implements AlgorithmTrace {

    private static final double TOLERANCE = 1e-9;
    private static final int MAX_EXCERPT_SIZE = 8;
    private static final double SQRT_2 = Math.sqrt(2.0);

    public EmbeddingApiTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(vectorAExcerpt, "vectorAExcerpt");
        Objects.requireNonNull(vectorBExcerpt, "vectorBExcerpt");
        Objects.requireNonNull(vectorA, "vectorA");
        Objects.requireNonNull(vectorB, "vectorB");
        Objects.requireNonNull(providerStatus, "providerStatus");
        vectorAExcerpt = List.copyOf(vectorAExcerpt);
        vectorBExcerpt = List.copyOf(vectorBExcerpt);
        vectorA = List.copyOf(vectorA);
        vectorB = List.copyOf(vectorB);

        if (providerStatus.isBlank()) {
            throw new IllegalArgumentException("providerStatus must not be blank");
        }
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive, was " + dimension);
        }
        if (vectorA.size() != dimension) {
            throw new IllegalArgumentException(
                    "vectorA must have size == dimension (%d), was %d".formatted(dimension, vectorA.size()));
        }
        if (vectorB.size() != dimension) {
            throw new IllegalArgumentException(
                    "vectorB must have size == dimension (%d), was %d".formatted(dimension, vectorB.size()));
        }

        int expectedExcerptSize = Math.min(MAX_EXCERPT_SIZE, dimension);
        if (vectorAExcerpt.size() != expectedExcerptSize
                || !vectorAExcerpt.equals(vectorA.subList(0, expectedExcerptSize))) {
            throw new IllegalArgumentException(
                    "vectorAExcerpt must be exactly the first %d dimensions of vectorA".formatted(expectedExcerptSize));
        }
        if (vectorBExcerpt.size() != expectedExcerptSize
                || !vectorBExcerpt.equals(vectorB.subList(0, expectedExcerptSize))) {
            throw new IllegalArgumentException(
                    "vectorBExcerpt must be exactly the first %d dimensions of vectorB".formatted(expectedExcerptSize));
        }

        if (!Double.isFinite(preNormL2A) || preNormL2A < 0) {
            throw new IllegalArgumentException(
                    "preNormL2A must be a non-negative finite number, was " + preNormL2A);
        }
        if (!Double.isFinite(preNormL2B) || preNormL2B < 0) {
            throw new IllegalArgumentException(
                    "preNormL2B must be a non-negative finite number, was " + preNormL2B);
        }

        double expectedSumSquaredDiff = 0.0;
        for (int i = 0; i < dimension; i++) {
            double diff = vectorA.get(i) - vectorB.get(i);
            expectedSumSquaredDiff += diff * diff;
        }
        if (!Double.isFinite(sumSquaredDiff) || sumSquaredDiff < 0) {
            throw new IllegalArgumentException(
                    "sumSquaredDiff must be a non-negative finite number, was " + sumSquaredDiff);
        }
        if (Math.abs(sumSquaredDiff - expectedSumSquaredDiff) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "sumSquaredDiff must equal the sum of squared differences of vectorA and vectorB (%.12f), was %.12f"
                            .formatted(expectedSumSquaredDiff, sumSquaredDiff));
        }

        double expectedDistance = Math.sqrt(expectedSumSquaredDiff);
        if (!Double.isFinite(distance) || distance < 0) {
            throw new IllegalArgumentException("distance must be a non-negative finite number, was " + distance);
        }
        if (Math.abs(distance - expectedDistance) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "distance must equal sqrt(sumSquaredDiff) (%.12f), was %.12f"
                            .formatted(expectedDistance, distance));
        }

        double expectedNormalizedScore = Math.max(0.0, Math.min(1.0, 1.0 - distance / SQRT_2));
        if (Math.abs(normalizedScore - expectedNormalizedScore) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "normalizedScore must equal clamp(1 - distance/sqrt(2), 0, 1) (%.12f), was %.12f"
                            .formatted(expectedNormalizedScore, normalizedScore));
        }
    }
}
