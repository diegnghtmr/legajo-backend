package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.similarity.JaccardTrace;

import java.util.List;
import java.util.Objects;

/** Wire shape of the Jaccard trace (TRD §6.3): both sets, their intersection/union, sizes, and the coefficient. */
public record JaccardTraceResponse(
        String algorithmId,
        List<String> setA,
        List<String> setB,
        int intersectionSize,
        int unionSize,
        List<String> intersection,
        List<String> union,
        double coefficient) implements AlgorithmTraceResponse {

    public static JaccardTraceResponse from(JaccardTrace trace) {
        Objects.requireNonNull(trace, "trace");
        return new JaccardTraceResponse(
                trace.algorithmId(), trace.setA(), trace.setB(), trace.intersectionSize(), trace.unionSize(),
                trace.intersection(), trace.union(), trace.coefficient());
    }
}
