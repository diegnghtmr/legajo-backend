package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.LinkageStep;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/**
 * The cophenetic distance matrix derived from a merge tree: the cophenetic
 * distance between observations i and j is the merge height of the first cluster that
 * contains both.
 *
 * <p><b>n=5 golden fixture, hand-derived independently of the implementation.</b> Reuses
 * the single-linkage golden from {@code LanceWilliamsEngineTest} (five points on a line at
 * 0,1,2,10,11): rows (0,1,1,2)(2,5,1,3)(3,4,1,2)(6,7,8,5). Walking the tree by hand: {0,1}
 * first join at height 1 (row 0); {2} joins {0,1} at height 1 (row 1), so cophenetic(0,2) =
 * cophenetic(1,2) = 1; {3,4} first join at height 1 (row 2); {0,1,2} and {3,4} first join
 * at height 8 (row 3), so every cross pair among {0,1,2} x {3,4} is 8. Full matrix:
 * <pre>
 *      0  1  2  3  4
 *   0  0  1  1  8  8
 *   1  1  0  1  8  8
 *   2  1  1  0  8  8
 *   3  8  8  8  0  1
 *   4  8  8  8  1  0
 * </pre>
 */
class CopheneticDistancesTest {

    private static final double TOLERANCE = 1e-9;

    private static LinkageMatrix fiveByFiveSingleLinkageGolden() {
        return new LinkageMatrix(List.of(
                new LinkageStep(0, 1, 1.0, 2),
                new LinkageStep(2, 5, 1.0, 3),
                new LinkageStep(3, 4, 1.0, 2),
                new LinkageStep(6, 7, 8.0, 5)));
    }

    @Test
    void matchesTheHandDerivedGoldenMatrix() {
        double[][] expected = {
                {0, 1, 1, 8, 8},
                {1, 0, 1, 8, 8},
                {1, 1, 0, 8, 8},
                {8, 8, 8, 0, 1},
                {8, 8, 8, 1, 0},
        };

        double[][] actual = CopheneticDistances.of(fiveByFiveSingleLinkageGolden());

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                assertThat(actual[i][j]).as("[%d][%d]", i, j).isCloseTo(expected[i][j], within(TOLERANCE));
            }
        }
    }

    @Test
    void isSymmetric() {
        double[][] matrix = CopheneticDistances.of(fiveByFiveSingleLinkageGolden());

        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix.length; j++) {
                assertThat(matrix[i][j]).as("[%d][%d] vs [%d][%d]", i, j, j, i)
                        .isCloseTo(matrix[j][i], within(TOLERANCE));
            }
        }
    }

    @Test
    void hasAZeroDiagonal() {
        double[][] matrix = CopheneticDistances.of(fiveByFiveSingleLinkageGolden());

        for (int i = 0; i < matrix.length; i++) {
            assertThat(matrix[i][i]).as("diagonal[%d]", i).isCloseTo(0.0, within(TOLERANCE));
        }
    }

    @Test
    void degenerateSingleObservationHasATrivialOneByOneMatrix() {
        LinkageMatrix noMerges = new LinkageMatrix(List.of());

        double[][] matrix = CopheneticDistances.of(noMerges);

        assertThat(matrix).hasDimensions(1, 1);
        assertThat(matrix[0][0]).isCloseTo(0.0, within(TOLERANCE));
    }

    @Test
    void ofRejectsANullLinkageMatrix() {
        assertThatNullPointerException().isThrownBy(() -> CopheneticDistances.of(null));
    }
}
