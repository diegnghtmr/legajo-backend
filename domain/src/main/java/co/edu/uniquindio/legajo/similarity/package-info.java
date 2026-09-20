/**
 * Text similarity capabilities: all six of RF1 (TRD §6.3, PRD HU-1.x) are implemented here.
 * {@link SimilarityAlgorithm} is the sealed contract every capability implements;
 * {@link Levenshtein} (S2) and {@link NeedlemanWunsch} (S3) are the two DP-based
 * capabilities, sharing the {@link DpMatrixTrace} model; {@link Jaccard} (S4) is the
 * set-based capability, with its own {@link JaccardTrace}; {@link TfIdfCosine} (S5) is the
 * corpus-aware capability (its corpus-wide df/N state lives in {@link TfIdfCorpusIndex},
 * carried through {@link SimilarityContext}), with its own {@link TfIdfCosineTrace};
 * {@link EmbeddingLocal} (S6a, offline MiniLM precompute) and {@link EmbeddingApi} (S6b,
 * OpenAI-compatible API precompute) are the two embedding-based capabilities, each with its
 * own trace ({@link EmbeddingLocalTrace}, {@link EmbeddingApiTrace}) and cached-vector model
 * ({@link EmbeddingVector}, read through {@link EmbeddingCache}). {@link AlgorithmKind}
 * distinguishes the four classical capabilities from the two AI-based ones; a caller collects
 * every Spring-wired implementation into a {@link SimilarityAlgorithmRegistry} to look one up
 * by id or kind. {@link NumericGuards} centralizes the finiteness guards every trace's compact
 * constructor needs for its tolerance and range comparisons. {@link Representation} names the
 * three vector-space representations RF2's clustering distance base can select (TRD §6.4).
 */
@NullMarked
package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.NullMarked;
