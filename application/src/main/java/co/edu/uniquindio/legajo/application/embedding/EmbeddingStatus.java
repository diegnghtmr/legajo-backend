package co.edu.uniquindio.legajo.application.embedding;

import java.util.Objects;

/**
 * {@code GET /embeddings/status}: provider, model, dimension, device, cached/live
 * mode, the cache's {@code corpusSha256}, and whether it matches the currently loaded corpus.
 */
public record EmbeddingStatus(
        String provider,
        String model,
        int dimension,
        String device,
        EmbeddingProviderMode mode,
        String corpusSha256,
        boolean matchesCorpus) {

    public EmbeddingStatus {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(corpusSha256, "corpusSha256");
    }
}
