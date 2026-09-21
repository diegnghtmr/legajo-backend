package co.edu.uniquindio.legajo.application.similarity;

import co.edu.uniquindio.legajo.similarity.SimilarityResult;

import java.util.Objects;

/**
 * One similarity result plus whether it was served from the request-keyed cache (TRD §6.6's
 * {@code cached} field, task A5) — {@code POST /similarity/matrix}'s per-cell shape,
 * mirroring what {@link AlgorithmSimilarity} carries for {@code POST /similarity/compare}. A
 * separate type from {@link AlgorithmSimilarity} because a matrix cell has no
 * {@code algorithmId} of its own (one algorithm applies to every cell in the grid).
 */
public record CachedSimilarityResult(SimilarityResult result, boolean cached) {

    public CachedSimilarityResult {
        Objects.requireNonNull(result, "result");
    }
}
