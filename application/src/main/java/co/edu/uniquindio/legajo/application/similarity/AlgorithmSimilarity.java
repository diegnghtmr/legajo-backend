package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.similarity.SimilarityResult;

import java.util.Objects;

/**
 * One algorithm's result within a {@code POST /similarity/compare} response,
 * plus whether it was served from the request-keyed cache rather than freshly
 * computed by this call.
 */
public record AlgorithmSimilarity(String algorithmId, SimilarityResult result, boolean cached) {

    public AlgorithmSimilarity {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(result, "result");
    }
}
