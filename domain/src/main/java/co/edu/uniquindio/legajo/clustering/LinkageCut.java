package co.edu.uniquindio.legajo.clustering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Flat-cluster extraction by cutting a {@link LinkageMatrix} at a fixed {@code k}: the cut is
 * by k alone, with k in [2, n-1], and there is deliberately no height-based cut. This is a
 * building block for later consumers — mean silhouette, Davies-Bouldin and the ranking rule —
 * which all evaluate a fixed-{@code k} partition, and none of them can exist without first
 * turning a merge tree into cluster membership.
 *
 * <p><b>How the cut works.</b> Undoing the last {@code k-1} merges is the same thing as
 * keeping only the first {@code n-k} merges (the fixed {@code k} range guarantees
 * {@code 1 <= n-k <= n-2}, so there is always at least one merge kept and at least one
 * undone). This class replays exactly those first {@code n-k} {@link LinkageStep} rows
 * through a union-find over the merge tree's ids — {@code 0..n-1} for the original
 * observations, {@code n+i} for the cluster created by row {@code i}, exactly the
 * convention {@link LinkageMatrix} already documents — then reads off which of the
 * {@code k} surviving roots each original observation belongs to.
 *
 * <p><b>Deterministic labels.</b> A union-find's root ids are an
 * accident of merge order, not a stable label a caller (mean silhouette, Davies-Bouldin,
 * the ranking rule, and eventually the REST layer
 * and the frontend) can rely on. This class instead orders the {@code k} surviving clusters
 * by their smallest member's original index and labels them {@code 0..k-1} in that order, so
 * the same linkage matrix always yields the same labels regardless of how the union-find's
 * roots happened to be numbered internally.
 *
 * <p>Assumes {@code linkage} is a well-formed merge tree over {@code 0..n-1} as produced by
 * {@link LanceWilliamsEngine} — the only producer this package exposes. {@link LinkageStep}
 * and {@link LinkageMatrix} validate their own local invariants (ordering, non-negativity,
 * monotonicity) but not global tree well-formedness, the same trust boundary
 * {@link LanceWilliamsEngineTest} already documents for the engine's output.
 */
public final class LinkageCut {

    private LinkageCut() {
    }

    /**
     * Cuts {@code linkage} at {@code k}, returning one label per original observation.
     * {@code k} must be in {@code [2, n-1]} where {@code n = linkage.size() + 1};
     * there is deliberately no height-based cut.
     */
    public static ClusterAssignment cut(LinkageMatrix linkage, int k) {
        Objects.requireNonNull(linkage, "linkage");
        int n = linkage.size() + 1;
        if (k < 2 || k > n - 1) {
            throw new IllegalArgumentException(
                    "k must be in [2, n-1] (n=%d), was %d".formatted(n, k));
        }

        int mergesToKeep = n - k;
        int totalNodes = 2 * n - 1;
        int[] parent = new int[totalNodes];
        for (int i = 0; i < totalNodes; i++) {
            parent[i] = i;
        }
        List<LinkageStep> rows = linkage.rows();
        for (int i = 0; i < mergesToKeep; i++) {
            LinkageStep row = rows.get(i);
            int newId = n + i;
            parent[find(parent, row.idx1())] = newId;
            parent[find(parent, row.idx2())] = newId;
        }

        // LinkedHashMap + ascending leaf scan: each cluster's member list is built in
        // ascending leaf order, so its first entry is already its smallest member.
        Map<Integer, List<Integer>> membersByRoot = new LinkedHashMap<>();
        for (int leaf = 0; leaf < n; leaf++) {
            membersByRoot.computeIfAbsent(find(parent, leaf), key -> new ArrayList<>()).add(leaf);
        }

        List<List<Integer>> clusters = new ArrayList<>(membersByRoot.values());
        clusters.sort((a, b) -> Integer.compare(a.get(0), b.get(0)));

        int[] labelOf = new int[n];
        for (int label = 0; label < clusters.size(); label++) {
            for (int member : clusters.get(label)) {
                labelOf[member] = label;
            }
        }

        List<Integer> labels = new ArrayList<>(n);
        for (int label : labelOf) {
            labels.add(label);
        }
        return new ClusterAssignment(labels, clusters.size());
    }

    private static int find(int[] parent, int id) {
        while (parent[id] != id) {
            id = parent[id];
        }
        return id;
    }
}
