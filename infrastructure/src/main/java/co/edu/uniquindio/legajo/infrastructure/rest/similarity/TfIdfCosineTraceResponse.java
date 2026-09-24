package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.TfIdfCosineTrace;

import java.util.List;
import java.util.Objects;

/** Wire shape of the {@code tfidf-cosine} trace: per-term weights plus the dot product, norms, cosine, and angle. */
public record TfIdfCosineTraceResponse(
        String algorithmId,
        int corpusSize,
        List<TfIdfTermTraceResponse> terms,
        double dotProduct,
        double rawNormA,
        double rawNormB,
        double cosine,
        double angleDegrees) implements AlgorithmTraceResponse {

    public static TfIdfCosineTraceResponse from(TfIdfCosineTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new TfIdfCosineTraceResponse(
                trace.algorithmId(), trace.corpusSize(),
                trace.terms().stream().map(TfIdfTermTraceResponse::from).toList(),
                trace.dotProduct(), trace.rawNormA(), trace.rawNormB(), trace.cosine(), trace.angleDegrees());
    }
}
