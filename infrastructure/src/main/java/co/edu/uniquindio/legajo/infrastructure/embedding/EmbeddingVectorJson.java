package co.edu.uniquindio.legajo.infrastructure.embedding;

import java.util.List;

/**
 * Wire shape of one vector entry in an {@code embeddings-*.json} cache (TRD §9): {@code id}
 * (the document id), {@code preNormL2} (provenance, TRD §6.3), and {@code values} (the
 * L2-normalized vector as written; renormalized again, unconditionally, on load —
 * {@link JsonEmbeddingRepository} owns that step, not this DTO).
 */
record EmbeddingVectorJson(String id, double preNormL2, List<Double> values) {
}
