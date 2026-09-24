package co.edu.uniquindio.legajo.infrastructure.rest.embedding;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingStatus;

import java.util.Objects;

/**
 * The {@code embedding-local} object within {@code GET /api/v1/embeddings/status}:
 * provider, model, dimension, the cache's {@code corpusSha256}
 * and {@code matchesCorpus}, plus {@code device} — the deployment fact that only
 * applies to this family, since its inference happens exclusively during offline precompute,
 * CPU-only by default.
 */
public record EmbeddingLocalStatusResponse(
        String provider, String model, int dimension, String corpusSha256, boolean matchesCorpus, String device) {

    public static EmbeddingLocalStatusResponse from(EmbeddingStatus status) {
        Objects.requireNonNull(status, "status");
        return new EmbeddingLocalStatusResponse(status.provider(), status.model(), status.dimension(),
                status.corpusSha256(), status.matchesCorpus(), status.device());
    }
}
