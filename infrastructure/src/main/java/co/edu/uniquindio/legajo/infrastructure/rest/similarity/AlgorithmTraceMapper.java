package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.AlgorithmTrace;
import co.edu.uniquindio.legajo.similarity.DpMatrixTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingApiTrace;
import co.edu.uniquindio.legajo.similarity.EmbeddingLocalTrace;
import co.edu.uniquindio.legajo.similarity.JaccardTrace;
import co.edu.uniquindio.legajo.similarity.TfIdfCosineTrace;

import java.util.Objects;

/**
 * Maps the domain's sealed {@link AlgorithmTrace} to its matching {@link
 * AlgorithmTraceResponse} DTO. An exhaustive {@code switch} over a sealed interface with no
 * {@code default} branch — the compiler itself, not a runtime check, forces this mapper to
 * be revisited the day a seventh trace type is added to the domain.
 */
public final class AlgorithmTraceMapper {

    private AlgorithmTraceMapper() {
    }

    public static AlgorithmTraceResponse toResponse(AlgorithmTrace trace, boolean stemming) {
        Objects.requireNonNull(trace, "trace");
        return switch (trace) {
            case DpMatrixTrace t -> DpMatrixTraceResponse.from(t, stemming);
            case JaccardTrace t -> JaccardTraceResponse.from(t, stemming);
            case TfIdfCosineTrace t -> TfIdfCosineTraceResponse.from(t, stemming);
            case EmbeddingLocalTrace t -> EmbeddingLocalTraceResponse.from(t, stemming);
            case EmbeddingApiTrace t -> EmbeddingApiTraceResponse.from(t, stemming);
        };
    }
}
