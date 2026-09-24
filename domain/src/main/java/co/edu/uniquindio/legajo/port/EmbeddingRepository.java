package co.edu.uniquindio.legajo.port;

import co.edu.uniquindio.legajo.similarity.EmbeddingCache;

/**
 * Output port for one embedding cache file: {@code domain} depends only on this
 * interface. The JSON adapter that reads/writes {@code data/embeddings-minilm.json} — and,
 * later, {@code data/embeddings-openai.json} against the same schema — is an infrastructure
 * concern, not part of this module.
 *
 * <p>This port is deliberately narrower than the broader {@code EmbeddingProvider} concept,
 * which also resolves the pair of vectors for a comparison and mixes in the cached-vs-live
 * provider choice for {@code embedding-api}: that broader orchestration is an
 * application-layer concern for a later task. This port only
 * covers the persistence side — loading and saving one cache file's {@link EmbeddingCache}
 * shape — the same responsibility {@link CorpusRepository} has for {@code corpus.json}.
 */
public interface EmbeddingRepository {

    /**
     * Loads the embedding cache currently on disk, renormalizing every vector to unit
     * length unconditionally (the fixed unit-norm invariant) and
     * failing closed if the cache's {@code corpusSha256} does not match the corpus this
     * repository was bound to.
     */
    EmbeddingCache load();

    /** Writes {@code cache} to disk in the fixed schema, replacing any previous content. */
    void save(EmbeddingCache cache);
}
