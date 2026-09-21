package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.similarity.SimilarityResult;

import java.util.Objects;

/** One algorithm's result within a {@code POST /similarity/compare} response (TRD §6.6). */
public record AlgorithmSimilarity(String algorithmId, SimilarityResult result) {

    public AlgorithmSimilarity {
        Objects.requireNonNull(algorithmId, "algorithmId");
        Objects.requireNonNull(result, "result");
    }
}
