/**
 * Text similarity capabilities: all six are implemented here.
 * {@link SimilarityAlgorithm} is the sealed contract every capability implements;
 * {@link Levenshtein} and {@link NeedlemanWunsch} are the two DP-based
 * capabilities, sharing the {@link DpMatrixTrace} model; {@link Jaccard} is the
 * set-based capability, with its own {@link JaccardTrace}; {@link TfIdfCosine} is the
 * corpus-aware capability (its corpus-wide df/N state lives in {@link TfIdfCorpusIndex},
 * carried through {@link SimilarityContext}), with its own {@link TfIdfCosineTrace};
 * {@link EmbeddingLocal} (offline MiniLM precompute) and {@link EmbeddingApi}
 * (OpenAI-compatible API precompute) are the two embedding-based capabilities, each with its
 * own trace ({@link EmbeddingLocalTrace}, {@link EmbeddingApiTrace}) and cached-vector model
 * ({@link EmbeddingVector}, read through {@link EmbeddingCache}). {@link AlgorithmKind}
 * distinguishes the four classical capabilities from the two AI-based ones; a caller collects
 * every Spring-wired implementation into a {@link SimilarityAlgorithmRegistry} to look one up
 * by id or kind. {@link NumericGuards} centralizes the finiteness guards every trace's compact
 * constructor needs for its tolerance and range comparisons. {@link Representation} names the
 * three vector-space representations the clustering distance base can select;
 * {@link TfIdfCorpusVectors} materializes the full-vocabulary, L2-normalized TF-IDF vectors
 * that representation needs when it is {@code tfidf-cosine} (the Davies–Bouldin metric
 * needs every document's vector in one shared space — {@link TfIdfCosine}'s own
 * pairwise vectors are scoped to just the compared pair's terms).
 */
@NullMarked
package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.NullMarked;
