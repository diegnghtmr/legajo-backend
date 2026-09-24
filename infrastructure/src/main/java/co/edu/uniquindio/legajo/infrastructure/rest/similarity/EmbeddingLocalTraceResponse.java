package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.EmbeddingLocalTrace;

import java.util.List;
import java.util.Objects;

/** Wire shape of the {@code embedding-local} trace: vector excerpt/full vectors, norms, cosine, angle, and score. */
public record EmbeddingLocalTraceResponse(
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
        double normalizedScore) implements AlgorithmTraceResponse {

    public static EmbeddingLocalTraceResponse from(EmbeddingLocalTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new EmbeddingLocalTraceResponse(
                trace.algorithmId(), trace.provider(), trace.model(), trace.dimension(),
                trace.vectorAExcerpt(), trace.vectorBExcerpt(), trace.vectorA(), trace.vectorB(),
                trace.preNormL2A(), trace.preNormL2B(), trace.dotProduct(), trace.cosine(), trace.angleDegrees(),
                trace.normalizedScore());
    }
}
