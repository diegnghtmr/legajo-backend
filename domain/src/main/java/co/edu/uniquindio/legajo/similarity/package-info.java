/**
 * Text similarity capabilities: Levenshtein, Needleman-Wunsch, Jaccard, TF-IDF cosine,
 * local embeddings, and API embeddings (TRD §6.3). {@link SimilarityAlgorithm} is the
 * sealed contract every capability implements; {@link Levenshtein} is the first
 * permitted type (S1 identity only, full DP implementation in S2). The remaining five
 * capabilities join the sealed permits list in later tasks.
 */
@NullMarked
package co.edu.uniquindio.legajo.similarity;

import org.jspecify.annotations.NullMarked;
