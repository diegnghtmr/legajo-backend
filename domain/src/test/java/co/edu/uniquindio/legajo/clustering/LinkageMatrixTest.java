package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The full linkage matrix (TRD §6.4): n-1 {@link LinkageStep} rows in merge order, with
 * merge distances monotone non-decreasing (TRD §6.4, "distancias de fusión monótonas no
 * decrecientes"). {@link LanceWilliamsEngine} (R3) is the only intended producer, but this
 * type validates the invariant itself — a hand-built violating list must still be rejected,
 * following the house rule that a validated value type re-derives and checks its own
 * invariants rather than trusting its caller.
 */
class LinkageMatrixTest {

    @Test
    void rowsAreExposedInOrder() {
        LinkageStep first = new LinkageStep(0, 1, 1.0, 2);
        LinkageStep second = new LinkageStep(2, 3, 2.0, 2);

        LinkageMatrix matrix = new LinkageMatrix(List.of(first, second));

        assertThat(matrix.rows()).containsExactly(first, second);
        assertThat(matrix.size()).isEqualTo(2);
    }

    @Test
    void rejectsANullRowList() {
        assertThatNullPointerException().isThrownBy(() -> new LinkageMatrix(null));
    }

    @Test
    void acceptsAnEmptyRowList() {
        // The degenerate n=1 case: nothing to merge, so an empty linkage matrix is valid.
        LinkageMatrix matrix = new LinkageMatrix(List.of());

        assertThat(matrix.rows()).isEmpty();
        assertThat(matrix.size()).isEqualTo(0);
    }

    @Test
    void rejectsADecreasingMergeDistance() {
        LinkageStep first = new LinkageStep(0, 1, 2.0, 2);
        LinkageStep second = new LinkageStep(2, 3, 1.0, 2);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LinkageMatrix(List.of(first, second)))
                .withMessageContaining("monotone");
    }

    @Test
    void rowsIsDefensivelyImmutable() {
        LinkageStep first = new LinkageStep(0, 1, 1.0, 2);
        LinkageMatrix matrix = new LinkageMatrix(new java.util.ArrayList<>(List.of(first)));

        assertThatThrownBy(() -> matrix.rows().add(new LinkageStep(2, 3, 2.0, 2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void equalsAndHashCodeCompareByContent() {
        LinkageStep first = new LinkageStep(0, 1, 1.0, 2);
        LinkageMatrix a = new LinkageMatrix(List.of(first));
        LinkageMatrix b = new LinkageMatrix(List.of(new LinkageStep(0, 1, 1.0, 2)));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
