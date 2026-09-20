/**
 * Text similarity capabilities: Levenshtein, Needleman-Wunsch, Jaccard, TF-IDF cosine,
 * local embeddings, and API embeddings (TRD §6.3). {@link SimilarityAlgorithm} is the
 * sealed contract every capability implements; {@link Levenshtein} (S2) and
 * {@link NeedlemanWunsch} (S3) are the two DP-based capabilities, sharing the
 * {@link DpMatrixTrace} model; {@link Jaccard} (S4) is the set-based capability, with its
 * own {@link JaccardTrace}; {@link TfIdfCosine} (S5) is the corpus-aware capability (its
 * corpus-wide df/N state lives in {@link TfIdfCorpusIndex}, carried through
 * {@link SimilarityContext}), with its own {@link TfIdfCosineTrace}. The remaining two
 * embedding capabilities join the sealed permits list in a later task.
 */
@NullMarked
package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.NullMarked;
