package co.edu.uniquindio.legajo.similarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hand-written Levenshtein edit-distance DP core (R-02), generic over {@code List<T>}
 * (TRD §13): the {@link Levenshtein} capability instantiates it over token lists, and
 * {@code LevenshteinCoreTest} exercises it directly over character lists for the
 * mandatory {@code kitten}/{@code sitting} = 3 check.
 *
 * <p>Standard Wagner–Fischer recurrence: {@code dp[0][j] = j}, {@code dp[i][0] = i},
 * {@code dp[i][j] = dp[i-1][j-1]} on a match, otherwise
 * {@code 1 + min(dp[i-1][j-1], dp[i-1][j], dp[i][j-1])} (substitution, deletion,
 * insertion). The backtrace resolves ties with the fixed order the TRD requires:
 * diagonal (match/substitution), then up (deletion), then left (insertion).
 */
final class LevenshteinCore {

    private LevenshteinCore() {
    }

    /** Full {@code (a.size()+1) x (b.size()+1)} DP matrix, kept entirely for the trace. */
    static <T> int[][] matrix(List<T> a, List<T> b) {
        int rows = a.size() + 1;
        int cols = b.size() + 1;
        int[][] dp = new int[rows][cols];

        for (int i = 0; i < rows; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j < cols; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i < rows; i++) {
            for (int j = 1; j < cols; j++) {
                if (Objects.equals(a.get(i - 1), b.get(j - 1))) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    int substitution = dp[i - 1][j - 1] + 1;
                    int deletion = dp[i - 1][j] + 1;
                    int insertion = dp[i][j - 1] + 1;
                    dp[i][j] = Math.min(substitution, Math.min(deletion, insertion));
                }
            }
        }
        return dp;
    }

    /** The edit distance itself, i.e. the matrix's bottom-right corner. */
    static <T> int distance(List<T> a, List<T> b) {
        int[][] dp = matrix(a, b);
        return dp[a.size()][b.size()];
    }

    /**
     * Backtraces {@code matrix} from the bottom-right corner to (0,0), applying the fixed
     * tie order (diagonal, up, left) at every step, and classifies each move.
     */
    static <T> Backtrace backtrace(List<T> a, List<T> b, int[][] matrix) {
        List<MatrixCell> path = new ArrayList<>();
        List<DpTraceStep> operations = new ArrayList<>();

        int i = a.size();
        int j = b.size();
        path.add(new MatrixCell(i, j));

        while (i > 0 || j > 0) {
            MatrixCell to = new MatrixCell(i, j);
            if (i > 0 && j > 0 && matches(a, b, i, j) && matrix[i][j] == matrix[i - 1][j - 1]) {
                i--;
                j--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.MATCH));
            } else if (i > 0 && j > 0 && matrix[i][j] == matrix[i - 1][j - 1] + 1) {
                i--;
                j--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.SUBSTITUTION));
            } else if (i > 0 && matrix[i][j] == matrix[i - 1][j] + 1) {
                i--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.DELETION));
            } else {
                j--;
                operations.add(new DpTraceStep(new MatrixCell(i, j), to, DpOperationKind.INSERTION));
            }
            path.add(new MatrixCell(i, j));
        }

        return new Backtrace(List.copyOf(path), List.copyOf(operations));
    }

    private static <T> boolean matches(List<T> a, List<T> b, int i, int j) {
        return Objects.equals(a.get(i - 1), b.get(j - 1));
    }

    /** {@code path} runs from the bottom-right corner to (0,0); {@code operations} has one entry per move. */
    record Backtrace(List<MatrixCell> path, List<DpTraceStep> operations) {
    }
}
