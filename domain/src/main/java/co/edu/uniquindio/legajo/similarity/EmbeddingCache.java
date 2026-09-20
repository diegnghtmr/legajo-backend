package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Domain shape of one {@code embeddings-*.json} cache file (TRD §9): the corpus binding
 * ({@code corpusVersion}/{@code corpusSha256}, TRD §6.1's frozen definition), {@code model}
 * and {@code dimension} metadata, and the per-document {@link EmbeddingVector} entries.
 *
 * <p>Framework-free, like {@link TfIdfCorpusIndex}: the infrastructure JSON adapter that
 * reads/writes {@code data/embeddings-minilm.json} builds and consumes this type through the
 * {@code EmbeddingRepository} output port, without either module depending on Jackson.
 */
public record EmbeddingCache(String version, String corpusVersion, String corpusSha256, String model, int dimension,
        List<EmbeddingVector> vectors) {

    public EmbeddingCache {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(corpusVersion, "corpusVersion");
        Objects.requireNonNull(corpusSha256, "corpusSha256");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(vectors, "vectors");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive, was " + dimension);
        }
        vectors = List.copyOf(vectors);
        for (EmbeddingVector vector : vectors) {
            if (vector.dimension() != dimension) {
                throw new IllegalArgumentException(
                        "vector for document '%s' has dimension %d, expected %d"
                                .formatted(vector.documentId(), vector.dimension(), dimension));
            }
        }
    }

    /** Finds the cached vector for {@code documentId}, if this cache holds one. */
    public Optional<EmbeddingVector> find(String documentId) {
        Objects.requireNonNull(documentId, "documentId");
        return vectors.stream().filter(vector -> vector.documentId().equals(documentId)).findFirst();
    }
}
