package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Flat-cluster extraction by cutting at a fixed k (cuts happen by k alone, with k in
 * [2, n-1]; no height-based cut). Uses the same n=5 hand-derived single-linkage golden
 * fixture as {@link LanceWilliamsEngineTest} (five points on a line at 0,1,2,10,11), whose
 * rows are (0,1,1,2)(2,5,1,3)(3,4,1,2)(6,7,8,5).
 */
class LinkageCutTest {

    private static LinkageMatrix fiveByFiveSingleLinkageGolden() {
        return new LinkageMatrix(List.of(
                new LinkageStep(0, 1, 1.0, 2),
                new LinkageStep(2, 5, 1.0, 3),
                new LinkageStep(3, 4, 1.0, 2),
                new LinkageStep(6, 7, 8.0, 5)));
    }

    @Test
    void cuttingAtKEqualsTwoUndoesOnlyTheLastMerge() {
        // Keeping the first n-k = 5-2 = 3 merges: {0,1,2} is one cluster (rows 0 and 1),
        // {3,4} is the other (row 2); row 3 (the final merge) is undone.
        ClusterAssignment assignment = LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 2);

        assertThat(assignment.k()).isEqualTo(2);
        assertThat(assignment.labels()).containsExactly(0, 0, 0, 1, 1);
    }

    @Test
    void cuttingAtKEqualsFourUndoesAllButTheFirstMerge() {
        // Keeping the first n-k = 5-4 = 1 merge: {0,1} is one cluster, {2}, {3}, {4} are
        // singletons. Labels ordered by ascending smallest member: {0,1}->0, {2}->1,
        // {3}->2, {4}->3.
        ClusterAssignment assignment = LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 4);

        assertThat(assignment.k()).isEqualTo(4);
        assertThat(assignment.labels()).containsExactly(0, 0, 1, 2, 3);
    }

    @Test
    void cuttingAtKEqualsNMinusOneIsTheFinestNonTrivialCut() {
        ClusterAssignment assignment = LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 4);

        assertThat(assignment.labels()).hasSize(5);
        assertThat(assignment.labels()).allSatisfy(label -> assertThat(label).isBetween(0, 3));
    }

    @Test
    void kBelowTwoIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 1))
                .withMessageContaining("[2, n-1]");
    }

    @Test
    void kAboveNMinusOneIsRejected() {
        // n=5, so n-1=4; k=5 is out of range.
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 5))
                .withMessageContaining("[2, n-1]");
    }

    @Test
    void cutRejectsANullLinkageMatrix() {
        assertThatNullPointerException().isThrownBy(() -> LinkageCut.cut(null, 2));
    }

    @Test
    void sameInputTwiceProducesTheSameLabels() {
        // Determinism requirement.
        ClusterAssignment first = LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 3);
        ClusterAssignment second = LinkageCut.cut(fiveByFiveSingleLinkageGolden(), 3);

        assertThat(first).isEqualTo(second);
    }
}
