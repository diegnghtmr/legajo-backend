package co.edu.uniquindio.legajo.infrastructure.rest.embedding;

import co.edu.uniquindio.legajo.application.embedding.EmbeddingStatus;

import java.util.Objects;

/**
 * The {@code embedding-api} object within {@code GET /api/v1/embeddings/status}:
 * provider, model, dimension, the cache's {@code corpusSha256} and
 * {@code matchesCorpus}, plus {@code mode} — {@code cached} or {@code live}
 * — the only capability with a live-update path. {@code mode} is
 * serialized as {@link co.edu.uniquindio.legajo.application.embedding.EmbeddingProviderMode#id()}
 * (the stable hyphen-free string, e.g. {@code "cached"}), mirroring every other id-bearing
 * field this API already exposes as its stable string id rather than a raw Java enum name.
 */
public record EmbeddingApiStatusResponse(
        String provider, String model, int dimension, String corpusSha256, boolean matchesCorpus, String mode) {

    public static EmbeddingApiStatusResponse from(EmbeddingStatus status) {
        Objects.requireNonNull(status, "status");
        return new EmbeddingApiStatusResponse(status.provider(), status.model(), status.dimension(),
                status.corpusSha256(), status.matchesCorpus(), status.mode().id());
    }
}
