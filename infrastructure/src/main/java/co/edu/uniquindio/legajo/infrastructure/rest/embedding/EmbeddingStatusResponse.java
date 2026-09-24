package co.edu.uniquindio.legajo.infrastructure.rest.embedding;

import java.util.Objects;

/**
 * Wire shape of {@code GET /api/v1/embeddings/status}: the status of both embedding
 * families in one response, one object per capability: one field per capability,
 * {@code embeddingLocal} and {@code embeddingApi} —
 * the camelCase spelling matches every other field name this API already uses
 * ({@code normalizedScore}, {@code computedNanos}, ...), and a field per capability (rather
 * than a map keyed by capability id) was chosen because the two capabilities' shapes
 * genuinely differ ({@code device} vs {@code mode}), so a fixed pair of named, statically
 * typed fields describes the contract more precisely than a map ever could. This field
 * shape is an author decision, not part of the fixed rule — which only fixes that both
 * families appear in one response.
 */
public record EmbeddingStatusResponse(
        EmbeddingLocalStatusResponse embeddingLocal, EmbeddingApiStatusResponse embeddingApi) {

    public EmbeddingStatusResponse {
        Objects.requireNonNull(embeddingLocal, "embeddingLocal");
        Objects.requireNonNull(embeddingApi, "embeddingApi");
    }
}
