package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hand-written Needleman-Wunsch global-alignment DP core (R-02), generic over
 * {@code List<T>} (TRD §6.3, ADR-012). The scoring constants are fixed in v1 and not
 * exposed for override anywhere: {@link #MATCH_SCORE} (+1), {@link #MISMATCH_SCORE} (-1),
 * {@link #GAP_SCORE} (-1).
 *
 * <p>Recurrence: {@code dp[i][0] = i * GAP_SCORE}, {@code dp[0][j] = j * GAP_SCORE},
 * {@code dp[i][j] = max(dp[i-1][j-1] + score(a[i-1], b[j-1]), dp[i-1][j] + GAP_SCORE,
 * dp[i][j-1] + GAP_SCORE)} — maximizing alignment score, unlike
 * {@link LevenshteinCore}'s minimized edit cost. The backtrace resolves ties with the
 * same fixed order the TRD requires for both DP capabilities: diagonal
 * (match/mismatch), then up (gap), then left (gap).
 */
final class NeedlemanWunschCore {

    static final int MATCH_SCORE = 1;
    static final int MISMATCH_SCORE = -1;
    static final int GAP_SCORE = -1;

    private NeedlemanWunschCore() {
    }

    /** Full {@code (a.size()+1) x (b.size()+1)} DP matrix, kept entirely for the trace. */
    static <T> int[][] matrix(List<T> a, List<T> b) {
        int rows = a.size() + 1;
        int cols = b.size() + 1;
        int[][] dp = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            dp[i][0] = i * GAP_SCORE;
        }
        for (int j = 0; j < cols; j++) {
            dp[0][j] = j * GAP_SCORE;
        }
        for (int i = 1; i < rows; i++) {
            for (int j = 1; j < cols; j++) {
                int diagonal = dp[i - 1][j - 1] + score(a, b, i, j);
                int up = dp[i - 1][j] + GAP_SCORE;
                int left = dp[i][j - 1] + GAP_SCORE;
                dp[i][j] = Math.max(diagonal, Math.max(up, left));
            }
        }
        return dp;
    }

    /** The optimal alignment score itself, i.e. the matrix's bottom-right corner. */
    static <T> int score(List<T> a, List<T> b) {
        int[][] dp = matrix(a, b);
        return dp[a.size()][b.size()];
    }

    /**
     * Backtraces {@code matrix} from the bottom-right corner to (0,0), applying the fixed
     * tie order (diagonal, up, left) at every step, and classifies each move as
     * {@link DpOperationKind#MATCH}, {@link DpOperationKind#MISMATCH}, or
     * {@link DpOperationKind#GAP} (a gap's direction is not distinguished, unlike
     * Levenshtein's insertion/deletion).
     */
    static <T> Backtrace backtrace(List<T> a, List<T> b, int[][] matrix) {
        List<MatrixCell> path = new ArrayList<>();
        List<DpTraceStep> operations = new ArrayList<>();

        int i = a.size();
        int j = b.size();
        path.add(new MatrixCell(i, j));

        while (i > 0 || j > 0) {
            MatrixCell to = new MatrixCell(i, j);
            if (i > 0 && j > 0 && matrix[i][j] == matrix[i - 1][j - 1] + score(a, b, i, j)) {
                boolean matched = matches(a, b, i, j);
                i--;
                j--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to,
                        matched ? DpOperationKind.MATCH : DpOperationKind.MISMATCH));
            } else if (i > 0 && matrix[i][j] == matrix[i - 1][j] + GAP_SCORE) {
                i--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.GAP));
            } else if (j > 0) {
                j--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.GAP));
            } else {
                // Unreachable when matrix comes from matrix(a, b) above: at every visited
                // cell one of the three backtrace rules always matches by construction. This
                // guard only fires for a matrix that violates that invariant (hand-built or
                // corrupted), and fails closed instead of silently decrementing j below 0.
                throw new IllegalStateException(
                        ("NeedlemanWunschCore backtrace reached an inconsistent state at (%d,%d): "
                                + "no valid predecessor cell (diagonal, up, or left) matches the given matrix")
                                        .formatted(i, j));
            }
            path.add(new MatrixCell(i, j));
        }

        return new Backtrace(List.copyOf(path), List.copyOf(operations));
    }

    private static <T> int score(List<T> a, List<T> b, int i, int j) {
        return matches(a, b, i, j) ? MATCH_SCORE : MISMATCH_SCORE;
    }

    private static <T> boolean matches(List<T> a, List<T> b, int i, int j) {
        return Objects.equals(a.get(i - 1), b.get(j - 1));
    }

    /** {@code path} runs from the bottom-right corner to (0,0); {@code operations} has one entry per move. */
    record Backtrace(List<MatrixCell> path, List<DpTraceStep> operations) {
    }
}
