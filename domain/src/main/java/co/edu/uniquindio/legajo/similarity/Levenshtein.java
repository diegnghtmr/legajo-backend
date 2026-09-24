package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Optional;

/**
 * Token-level Levenshtein edit distance, hand-written; no library implements it,
 * on top of the generic {@link LevenshteinCore}. {@code normalized = 1 - D /
 * max(lenA, lenB)}, with both token streams empty mapped to {@code 1.0} to avoid the
 * 0/0 case; {@code degenerate} stays {@code false} always — that flag is reserved
 * for the documented TF-IDF null-vector case, not for Levenshtein's empty-input result.
 * The trace exposes the full DP matrix, the row/column token labels, and the
 * deterministic optimal path (tie order: diagonal, then up, then left).
 */
public final class Levenshtein implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "levenshtein";
    }

    @Override
    public String displayName() {
        return "Levenshtein";
    }

    @Override
    public AlgorithmKind kind() {
        return AlgorithmKind.CLASSIC;
    }

    @Override
    public SimilarityResult compute(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        long start = System.nanoTime();

        List<String> tokensA = a.tokens();
        List<String> tokensB = b.tokens();
        int lenA = tokensA.size();
        int lenB = tokensB.size();
        int distance = LevenshteinCore.distance(tokensA, tokensB);
        double normalized = (lenA == 0 && lenB == 0)
                ? 1.0
                : 1.0 - (double) distance / Math.max(lenA, lenB);

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(normalized, (double) distance, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        List<String> tokensA = a.tokens();
        List<String> tokensB = b.tokens();

        int[][] matrix = LevenshteinCore.matrix(tokensA, tokensB);
        LevenshteinCore.Backtrace backtrace = LevenshteinCore.backtrace(tokensA, tokensB, matrix);

        AlgorithmTrace trace = new DpMatrixTrace(
                id(), tokensA, tokensB, toDoubleMatrix(matrix), backtrace.path(), backtrace.operations());
        return Optional.of(trace);
    }

    private static double[][] toDoubleMatrix(int[][] matrix) {
        double[][] result = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            result[i] = new double[matrix[i].length];
            for (int j = 0; j < matrix[i].length; j++) {
                result[i][j] = matrix[i][j];
            }
        }
        return result;
    }
}
