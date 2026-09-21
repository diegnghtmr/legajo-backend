package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.similarity.SimilarityResult;

import java.util.Objects;

/**
 * One algorithm's result within a {@code POST /similarity/compare} response (TRD §6.6),
 * plus whether it was served from the request-keyed cache (task A5) rather than freshly
 * computed by this call.
 */
public record AlgorithmSimilarity(String algorithmId, SimilarityResult result, boolean cached) {

    public AlgorithmSimilarity {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(result, "result");
    }
}
