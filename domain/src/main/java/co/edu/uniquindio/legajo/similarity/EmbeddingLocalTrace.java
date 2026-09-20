package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;

/**
 * Step-by-step evidence for {@code embedding-local} (TRD §6.3's embedding trace row): each
 * vector's first 8 dimensions and full dimension count, the dot product, each vector's
 * {@code preNormL2} recorded at precompute time (provenance only, not recomputed here), the
 * unit vectors themselves as stored in the cache, the cosine, the angle in degrees, the
 * {@code clamp(cos, 0, 1)} mapping applied, and provider/model metadata.
 *
 * <p>Validated invariants: {@code vectorA}/{@code vectorB} have size {@code dimension};
 * {@code vectorAExcerpt}/{@code vectorBExcerpt} are exactly the first
 * {@code min(8, dimension)} components of the corresponding full vector; {@code dotProduct}
 * equals the hand-computed dot product of {@code vectorA} and {@code vectorB};
 * {@code cosine} equals {@code dotProduct} (both vectors are unit length, TRD §6.3);
 * {@code angleDegrees} equals {@code degrees(acos(clamp(cosine, -1, 1)))}; and
 * {@code normalizedScore} equals {@code clamp(cosine, 0, 1)} (TRD §6.3, "Capacidades de
 * embedding (fijadas)").
 */
public record EmbeddingLocalTrace(
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
        double dotProduct,
        double cosine,
        double angleDegrees,
        double normalizedScore) implements AlgorithmTrace {

    private static final double TOLERANCE = 1e-9;
    private static final int MAX_EXCERPT_SIZE = 8;

    public EmbeddingLocalTrace {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(vectorAExcerpt, "vectorAExcerpt");
        Objects.requireNonNull(vectorBExcerpt, "vectorBExcerpt");
        Objects.requireNonNull(vectorA, "vectorA");
        Objects.requireNonNull(vectorB, "vectorB");
        vectorAExcerpt = List.copyOf(vectorAExcerpt);
        vectorBExcerpt = List.copyOf(vectorBExcerpt);
        vectorA = List.copyOf(vectorA);
        vectorB = List.copyOf(vectorB);

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

        double expectedDotProduct = 0.0;
        for (int i = 0; i < dimension; i++) {
            expectedDotProduct += vectorA.get(i) * vectorB.get(i);
        }
        if (Math.abs(dotProduct - expectedDotProduct) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "dotProduct must equal the dot product of vectorA and vectorB (%.12f), was %.12f"
                            .formatted(expectedDotProduct, dotProduct));
        }
        if (Math.abs(cosine - dotProduct) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "cosine must equal dotProduct for unit vectors (TRD §6.3), dotProduct was %.12f, cosine was %.12f"
                            .formatted(dotProduct, cosine));
        }

        double clampedForAngle = Math.max(-1.0, Math.min(1.0, cosine));
        double expectedAngle = Math.toDegrees(Math.acos(clampedForAngle));
        if (Math.abs(angleDegrees - expectedAngle) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "angleDegrees must equal degrees(acos(clamp(cosine, -1, 1))) (%.12f), was %.12f"
                            .formatted(expectedAngle, angleDegrees));
        }

        double expectedNormalizedScore = Math.max(0.0, Math.min(1.0, cosine));
        if (Math.abs(normalizedScore - expectedNormalizedScore) > TOLERANCE) {
            throw new IllegalArgumentException(
                    "normalizedScore must equal clamp(cosine, 0, 1) (%.12f), was %.12f"
                            .formatted(expectedNormalizedScore, normalizedScore));
        }
    }
}
