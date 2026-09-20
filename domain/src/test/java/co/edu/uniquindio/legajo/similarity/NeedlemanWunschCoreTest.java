package co.edu.uniquindio.legajo.similarity;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * {@link NeedlemanWunschCore} backtrace's final else-branch is unreachable when {@code matrix}
 * was produced by {@link NeedlemanWunschCore#matrix(List, List)} itself: by construction, at
 * every visited cell one of the three backtrace rules (diagonal, up, left) always matches. This
 * class proves the branch fails closed (instead of silently decrementing {@code j} into a
 * negative index) if it is ever reached through a matrix that violates that invariant, e.g. one
 * assembled by hand or corrupted in transit.
 */
class NeedlemanWunschCoreTest {

    @Test
    void backtraceThrowsOnAnInconsistentMatrixWithNoValidPredecessorCell() {
        List<Character> a = List.of('a');
        List<Character> b = List.of();
        // A well-formed 2x1 matrix has matrix[0][0] = 0 and matrix[1][0] = 1 * GAP_SCORE = -1
        // (the "up" recurrence dp[i][0] = i * GAP_SCORE). This one breaks that invariant, so
        // at (1,0) neither the diagonal rule (j == 0, so it never applies), the up rule
        // (matrix[1][0] == matrix[0][0] + GAP_SCORE -> 99 == -1, false), nor the left rule
        // (j == 0, so it never applies either) match.
        int[][] inconsistentMatrix = {{0}, {99}};

        assertThatIllegalStateException()
                .isThrownBy(() -> NeedlemanWunschCore.backtrace(a, b, inconsistentMatrix))
                .withMessageContaining("(1,0)");
    }
}
