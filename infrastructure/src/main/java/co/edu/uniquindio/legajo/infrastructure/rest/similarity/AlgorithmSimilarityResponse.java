package co.edu.uniquindio.legajo.infrastructure.rest.similarity;

import co.edu.uniquindio.legajo.application.similarity.AlgorithmSimilarity;

import java.util.Objects;

/** One algorithm's row within a {@code POST /similarity/compare} response. */
public record AlgorithmSimilarityResponse(String algorithmId, SimilarityResultResponse result) {

    public AlgorithmSimilarityResponse {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(result, "result");
    }

    public static AlgorithmSimilarityResponse from(AlgorithmSimilarity similarity, boolean stemming) {
        Objects.requireNonNull(similarity, "similarity");
        return new AlgorithmSimilarityResponse(
                similarity.algorithmId(), SimilarityResultResponse.from(similarity.result(), similarity.cached(), stemming));
    }
}
