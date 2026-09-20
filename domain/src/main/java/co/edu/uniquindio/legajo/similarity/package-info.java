/**
 * Text similarity capabilities: Levenshtein, Needleman-Wunsch, Jaccard, TF-IDF cosine,
 * local embeddings, and API embeddings (TRD §6.3). {@link SimilarityAlgorithm} is the
 * sealed contract every capability implements; {@link Levenshtein} (S2) and
 * {@link NeedlemanWunsch} (S3) are the two DP-based capabilities implemented so far,
 * sharing the {@link DpMatrixTrace} model. The remaining three capabilities join the
 * sealed permits list in later tasks.
 */
@NullMarked
package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.NullMarked;
