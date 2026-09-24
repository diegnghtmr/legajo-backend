package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * One row of a linkage matrix:
 * {@code (idx1, idx2, mergeDistance, size)} with {@code idx1 < idx2} always, {@code
 * mergeDistance} finite and non-negative, and {@code size} the number of original
 * observations folded into the cluster this merge creates (at least 2 — a merge always
 * combines two non-empty clusters).
 */
class LinkageStepTest {

    @Test
    void accessorsReturnTheGivenFields() {
        LinkageStep step = new LinkageStep(0, 1, 1.5, 2);

        assertThat(step.idx1()).isEqualTo(0);
        assertThat(step.idx2()).isEqualTo(1);
        assertThat(step.mergeDistance()).isEqualTo(1.5);
        assertThat(step.size()).isEqualTo(2);
    }

    @Test
    void rejectsIdx1EqualToIdx2() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LinkageStep(3, 3, 1.0, 2));
    }

    @Test
    void rejectsIdx1GreaterThanIdx2() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageStep(5, 2, 1.0, 2))
                .withMessageContaining("idx1");
    }

    @Test
    void rejectsANegativeIdx1() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LinkageStep(-1, 2, 1.0, 2));
    }

    @Test
    void rejectsANegativeIdx2() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageStep(0, -1, 1.0, 2))
                .withMessageContaining("idx2");
    }

    @Test
    void rejectsANonFiniteMergeDistance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageStep(0, 1, Double.NaN, 2));
    }

    @Test
    void rejectsANegativeMergeDistance() {
        assertThatIllegalArgumentException().isThrownBy(() -> new LinkageStep(0, 1, -0.5, 2));
    }

    @Test
    void rejectsASizeSmallerThanTwo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageStep(0, 1, 1.0, 1))
                .withMessageContaining("size");
    }
}
