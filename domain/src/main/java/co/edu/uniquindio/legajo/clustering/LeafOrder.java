package co.edu.uniquindio.legajo.clustering;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * The crossing-free leaf ordering a dendrogram SVG needs (TRD §6.4: "leafOrder para un SVG
 * sin cruces"). A depth-first traversal of the merge tree that visits, at each internal
 * node, {@code idx1}'s subtree fully before {@code idx2}'s, emits the original observations
 * in an order where every merge's two children occupy two contiguous, adjacent runs — the
 * property {@link LeafOrderPropertyTest} proves generatively. That contiguity is exactly
 * what lets a dendrogram draw every internal node as one vertical line spanning its
 * children's already-adjacent horizontal positions, with no branch crossing another.
 */
public final class LeafOrder {

    private LeafOrder() {
    }

    /**
     * Returns {@code linkage}'s original observations ({@code 0..n-1}) in crossing-free
     * order. For the degenerate {@code n=1} case ({@code linkage.size() == 0}) the only
     * possible order is the single leaf {@code 0}.
     */
    public static List<Integer> of(LinkageMatrix linkage) {
        Objects.requireNonNull(linkage, "linkage");
        int n = linkage.size() + 1;
        if (n == 1) {
            return List.of(0);
        }

        List<LinkageStep> rows = linkage.rows();
        int root = n + (rows.size() - 1);

        List<Integer> order = new ArrayList<>(n);
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(root);
        // Iterative pre-order DFS: pushing idx2 then idx1 leaves idx1 on top, so it is
        // popped (and its whole subtree expanded) before idx2 — matching the documented
        // "idx1 before idx2" rule without recursion depth limits.
        while (!stack.isEmpty()) {
            int id = stack.pop();
            if (id < n) {
                order.add(id);
                continue;
            }
            LinkageStep row = rows.get(id - n);
            stack.push(row.idx2());
            stack.push(row.idx1());
        }
        return order;
    }
}
