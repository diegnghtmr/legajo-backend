package co.edu.uniquindio.legajo.clustering;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * The crossing-free leaf ordering a dendrogram needs (TRD §6.4: "leafOrder para un SVG sin
 * cruces"). The order-correctness property itself (every merge's leaves form a contiguous
 * run) is proved generatively by {@link LeafOrderPropertyTest}; this class pins the
 * documented traversal rule (depth-first, {@code idx1} before {@code idx2}) against one
 * concrete tree and the degenerate {@code n=1} case.
 *
 * <p><b>n=5 golden fixture, hand-simulated independently of the implementation.</b> Reuses
 * {@link LanceWilliamsEngineTest}'s already-independently-verified single-linkage golden:
 * rows (0,1,1,2)(2,5,1,3)(3,4,1,2)(6,7,8,5), n=5, root id {@code n + (rows.size()-1) = 8}.
 * Simulating the documented rule (push {@code idx2} then {@code idx1} onto a stack, so
 * {@code idx1}'s subtree is fully visited before {@code idx2}'s) by hand: pop 8 (row
 * 3=(6,7)) -&gt; push 7,6; pop 6 (row 1=(2,5)) -&gt; push 5,2; pop 2 -&gt; leaf; pop 5 (row
 * 0=(0,1)) -&gt; push 1,0; pop 0 -&gt; leaf; pop 1 -&gt; leaf; pop 7 (row 2=(3,4)) -&gt; push
 * 4,3; pop 3 -&gt; leaf; pop 4 -&gt; leaf. Order: [2, 0, 1, 3, 4].
 */
class LeafOrderTest {

    private static LinkageMatrix fiveByFiveSingleLinkageGolden() {
        return new LinkageMatrix(List.of(
                new LinkageStep(0, 1, 1.0, 2),
                new LinkageStep(2, 5, 1.0, 3),
                new LinkageStep(3, 4, 1.0, 2),
                new LinkageStep(6, 7, 8.0, 5)));
    }

    @Test
    void matchesTheHandSimulatedTraversalOfTheGoldenTree() {
        List<Integer> order = LeafOrder.of(fiveByFiveSingleLinkageGolden());

        assertThat(order).containsExactly(2, 0, 1, 3, 4);
    }

    @Test
    void isAPermutationOfZeroToNMinusOne() {
        List<Integer> order = LeafOrder.of(fiveByFiveSingleLinkageGolden());

        assertThat(order).containsExactlyInAnyOrder(0, 1, 2, 3, 4);
    }

    @Test
    void degenerateSingleObservationOrdersToItself() {
        LinkageMatrix noMerges = new LinkageMatrix(List.of());

        List<Integer> order = LeafOrder.of(noMerges);

        assertThat(order).containsExactly(0);
    }

    @Test
    void ofRejectsANullLinkageMatrix() {
        assertThatNullPointerException().isThrownBy(() -> LeafOrder.of(null));
    }

    @Test
    void sameInputTwiceProducesTheSameOrder() {
        // NFR-QA-04 / TAC-10: determinism.
        List<Integer> first = LeafOrder.of(fiveByFiveSingleLinkageGolden());
        List<Integer> second = LeafOrder.of(fiveByFiveSingleLinkageGolden());

        assertThat(first).isEqualTo(second);
    }
}
