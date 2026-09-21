package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.EmbeddingApiTrace;

import java.util.List;
import java.util.Objects;

/** Wire shape of the {@code embedding-api} trace (TRD §6.3): vector excerpt/full vectors, Euclidean distance, score, and provider status. */
public record EmbeddingApiTraceResponse(
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
        String providerStatus) implements AlgorithmTraceResponse {

    public static EmbeddingApiTraceResponse from(EmbeddingApiTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new EmbeddingApiTraceResponse(
                trace.algorithmId(), trace.provider(), trace.model(), trace.dimension(),
                trace.vectorAExcerpt(), trace.vectorBExcerpt(), trace.vectorA(), trace.vectorB(),
                trace.preNormL2A(), trace.preNormL2B(), trace.sumSquaredDiff(), trace.distance(),
                trace.normalizedScore(), trace.providerStatus());
    }
}
