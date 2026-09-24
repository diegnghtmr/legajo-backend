package co.edu.uniquindio.legajo.similarity;

import java.util.List;
import java.util.Optional;

/**
 * Needleman-Wunsch global sequence alignment over tokens, hand-written; no library
 * implements it, on top of the generic {@link NeedlemanWunschCore}. The scoring constants are
 * fixed in v1 (match {@code +1}, mismatch {@code -1}, gap {@code -1}) and never editable
 * from any layer.
 *
 * <p>Normalization uses the fixed theoretical bounds, with
 * {@code m = min(lenA, lenB)} and {@code M = max(lenA, lenB)}: {@code S_min = mismatch·m +
 * gap·(M - m)} (a lower bound on the optimal score — always achievable by aligning the m
 * shared positions and gapping the rest), {@code S_max = match·M} (the absolute upper
 * bound), {@code normalized = 1.0} when both token streams are empty (M = 0, where the
 * quotient would be 0/0), and otherwise {@code normalized = (S - S_min) / (S_max - S_min)}
 * — which, algebraically, reduces to {@code (S + M) / (2·M)} once the fixed constants are
 * substituted (both forms are computed and asserted equal in
 * {@code NeedlemanWunschTest}). {@code degenerate} stays {@code false} always; that flag
 * is reserved for the TF-IDF null-vector case, not for this capability's
 * empty-input result.
 *
 * <p>The trace exposes the full DP matrix, the row/column token labels, and the
 * deterministic optimal path (fixed tie order: diagonal, then up, then left), with each
 * step classified as match, mismatch, or gap.
 */
public final class NeedlemanWunsch implements SimilarityAlgorithm {

    @Override
    public String id() {
        return "needleman-wunsch";
    }

    @Override
    public String displayName() {
        return "Needleman-Wunsch";
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
        int score = NeedlemanWunschCore.score(tokensA, tokensB);

        double normalized = normalize(score, lenA, lenB);

        long computedNanos = System.nanoTime() - start;
        return new SimilarityResult(normalized, (double) score, computedNanos);
    }

    @Override
    public Optional<AlgorithmTrace> trace(SimilarityInput a, SimilarityInput b, SimilarityContext context) {
        List<String> tokensA = a.tokens();
        List<String> tokensB = b.tokens();

        int[][] matrix = NeedlemanWunschCore.matrix(tokensA, tokensB);
        NeedlemanWunschCore.Backtrace backtrace = NeedlemanWunschCore.backtrace(tokensA, tokensB, matrix);

        AlgorithmTrace trace = new DpMatrixTrace(
                id(), tokensA, tokensB, toDoubleMatrix(matrix), backtrace.path(), backtrace.operations());
        return Optional.of(trace);
    }

    private static double normalize(int score, int lenA, int lenB) {
        if (lenA == 0 && lenB == 0) {
            return 1.0;
        }
        int m = Math.min(lenA, lenB);
        int bigM = Math.max(lenA, lenB);
        double sMin = (double) NeedlemanWunschCore.MISMATCH_SCORE * m
                + (double) NeedlemanWunschCore.GAP_SCORE * (bigM - m);
        double sMax = (double) NeedlemanWunschCore.MATCH_SCORE * bigM;
        return (score - sMin) / (sMax - sMin);
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
