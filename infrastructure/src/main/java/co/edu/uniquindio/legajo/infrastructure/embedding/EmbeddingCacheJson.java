package co.edu.uniquindio.legajo.infrastructure.embedding;

import java.util.List;

/**
 * Wire shape of an {@code embeddings-*.json} cache file, matching the fixed on-disk schema:
 * {@code version}, {@code corpusVersion}, {@code corpusSha256}, {@code model}, {@code
 * dimension}, then {@code vectors}, in that declaration order. Package-private: {@link
 * JsonEmbeddingRepository} is the only class that needs to see this DTO. Note there is no
 * {@code provider} field on the wire — the fixed schema does not carry one — because one
 * cache file always belongs to exactly one provider, known to the adapter that reads it, not
 * to the file itself.
 */
record EmbeddingCacheJson(String version, String corpusVersion, String corpusSha256, String model, int dimension,
        List<EmbeddingVectorJson> vectors) {
}
