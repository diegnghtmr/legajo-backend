package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Direct construction of {@link ClusterAssignment}, which re-validates its own shape rather
 * than trusting {@link LinkageCut} (cuts happen by k alone). Going through the cut only
 * ever produces well-formed assignments, so the invariant's own branches need a test that
 * builds the record by hand.
 */
class ClusterAssignmentTest {

    @Test
    void acceptsASurjectiveLabelling() {
        assertThatNoException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, 1, 1, 2, 0), 3));
    }

    /**
     * The shape range-checking alone lets through: every label sits inside [0, k), but three
     * of the five advertised clusters have no member. Every fixed-k consumer downstream —
     * mean silhouette, Davies-Bouldin, the ranking rule — iterates 0..k-1 and
     * would divide by an empty cluster's size.
     */
    @Test
    void rejectsLabelsThatLeaveAdvertisedClustersEmpty() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, 0), 5))
                .withMessageContaining("1 is empty");
    }

    @Test
    void rejectsAGapInTheMiddleOfTheLabelRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, 2, 2), 3))
                .withMessageContaining("1 is empty");
    }

    @Test
    void rejectsFewerObservationsThanClusters() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, 1), 3));
    }

    @Test
    void rejectsALabelOutsideTheRange() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, 3), 2))
                .withMessageContaining("every label must be in [0, k)");
    }

    @Test
    void rejectsANegativeLabel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0, -1), 2));
    }

    @Test
    void rejectsEmptyLabels() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(), 1))
                .withMessageContaining("must not be empty");
    }

    @Test
    void rejectsKBelowOne() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ClusterAssignment(List.of(0), 0))
                .withMessageContaining("k must be at least 1");
    }

    @Test
    void rejectsNullLabels() {
        assertThatNullPointerException().isThrownBy(() -> new ClusterAssignment(null, 1));
    }

    @Test
    void sizeReportsTheObservationCount() {
        assertThat(new ClusterAssignment(List.of(0, 1, 1), 2).size()).isEqualTo(3);
    }

    @Test
    void labelsAreDefensivelyCopied() {
        List<Integer> mutable = new java.util.ArrayList<>(List.of(0, 1));
        ClusterAssignment assignment = new ClusterAssignment(mutable, 2);

        mutable.set(0, 1);

        assertThat(assignment.labels()).containsExactly(0, 1);
    }
}
