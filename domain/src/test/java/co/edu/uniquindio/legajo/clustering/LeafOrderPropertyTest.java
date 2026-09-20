package co.edu.uniquindio.legajo.clustering;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property that actually makes a dendrogram SVG crossing-free (TRD §6.4, "leafOrder
 * para un SVG sin cruces"): for every merge row, the set of original observations under
 * that node occupies one contiguous run of positions in {@link LeafOrder#of(LinkageMatrix)}.
 * A test that only checks the output is a permutation of {@code 0..n-1} (covered by
 * {@link LeafOrderTest#isAPermutationOfZeroToNMinusOne()}) would pass for any shuffle and
 * proves nothing about crossings; this property recomputes each node's actual leaf set from
 * the linkage matrix and checks its span in {@code leafOrder} has exactly as many positions
 * as it has leaves — the definition of "contiguous run".
 *
 * <p>Generates random but valid merge trees: at each step two currently-active ids are
 * picked at random and merged, following the exact {@code id = n+i} convention
 * {@link LinkageMatrix} documents, with strictly increasing merge heights (so the matrix's
 * own monotonicity invariant holds) and accurate cluster sizes. This does not require the
 * tree to have come from an actual {@link LanceWilliamsEngine} run — {@link LeafOrder} only
 * depends on the {@code idx1}/{@code idx2}/id-numbering shape, exactly as
 * {@link LanceWilliamsEngineTest}'s own golden fixture (a hand-built distance matrix, not a
 * real cosine one) already establishes for this package's other structural tests.
 */
class LeafOrderPropertyTest {

    @Property
    void everyMergeNodeLeavesFormAContiguousRunInLeafOrder(@ForAll("randomLinkageMatrices") Fixture fixture) {
        LinkageMatrix linkage = fixture.linkage();
        int n = fixture.n();

        List<Integer> order = LeafOrder.of(linkage);
        assertThat(order).as("leafOrder must be a permutation of 0..n-1")
                .containsExactlyInAnyOrderElementsOf(rangeList(n));

        int[] positionOf = new int[n];
        for (int position = 0; position < n; position++) {
            positionOf[order.get(position)] = position;
        }

        Map<Integer, Set<Integer>> leavesUnder = new HashMap<>();
        for (int leaf = 0; leaf < n; leaf++) {
            leavesUnder.put(leaf, Set.of(leaf));
        }
        List<LinkageStep> rows = linkage.rows();
        for (int i = 0; i < rows.size(); i++) {
            LinkageStep row = rows.get(i);
            Set<Integer> merged = new HashSet<>(leavesUnder.get(row.idx1()));
            merged.addAll(leavesUnder.get(row.idx2()));
            leavesUnder.put(n + i, merged);

            int minPosition = merged.stream().mapToInt(leaf -> positionOf[leaf]).min().orElseThrow();
            int maxPosition = merged.stream().mapToInt(leaf -> positionOf[leaf]).max().orElseThrow();
            assertThat(maxPosition - minPosition + 1)
                    .as("merge row %d's %d leaves must occupy a contiguous run in leafOrder", i, merged.size())
                    .isEqualTo(merged.size());
        }
    }

    @Provide
    Arbitrary<Fixture> randomLinkageMatrices() {
        return Arbitraries.integers().between(2, 20)
                .flatMap(n -> Arbitraries.longs().map(seed -> new Fixture(n, randomLinkageMatrix(n, seed))));
    }

    private static LinkageMatrix randomLinkageMatrix(int n, long seed) {
        Random random = new Random(seed);
        List<Integer> active = new ArrayList<>();
        Map<Integer, Integer> sizeOf = new HashMap<>();
        for (int i = 0; i < n; i++) {
            active.add(i);
            sizeOf.put(i, 1);
        }

        List<LinkageStep> rows = new ArrayList<>(n - 1);
        for (int i = 0; i < n - 1; i++) {
            int firstIndex = random.nextInt(active.size());
            int first = active.remove(firstIndex);
            int secondIndex = random.nextInt(active.size());
            int second = active.remove(secondIndex);

            int idx1 = Math.min(first, second);
            int idx2 = Math.max(first, second);
            int size = sizeOf.get(first) + sizeOf.get(second);
            int newId = n + i;

            rows.add(new LinkageStep(idx1, idx2, i + 1.0, size));
            sizeOf.put(newId, size);
            active.add(newId);
        }
        return new LinkageMatrix(rows);
    }

    private static List<Integer> rangeList(int n) {
        List<Integer> range = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            range.add(i);
        }
        return range;
    }

    /** A generated tree paired with its {@code n}, since {@link LinkageMatrix} does not carry it. */
    record Fixture(int n, LinkageMatrix linkage) {
    }
}
