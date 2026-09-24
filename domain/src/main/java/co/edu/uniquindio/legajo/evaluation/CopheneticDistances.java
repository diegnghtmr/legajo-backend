package co.edu.uniquindio.legajo.evaluation;

import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.LinkageStep;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The cophenetic distance matrix derived from a merge tree: the cophenetic
 * distance between observations {@code i} and {@code j} is the merge height of the first
 * cluster that contains both. {@link CopheneticCorrelation} is this class's only intended
 * consumer, correlating this matrix against D as the primary ranking signal.
 *
 * <p><b>How it is built.</b> Walking {@code linkage}'s rows in merge order, row {@code i}
 * joins the leaf sets already accumulated under {@code idx1} and {@code idx2} (leaves are
 * their own singleton set at {@code i < n}); every cross pair between those two sets is
 * meeting for the first time, at exactly this row's {@code mergeDistance} — no pair is ever
 * revisited by a later, larger merge, so "first cluster that contains both" is simply
 * "the merge at which their two sets are joined". O(n²) time overall: the sum of
 * {@code |leftSet| * |rightSet|} across every merge is bounded by n².
 */
public final class CopheneticDistances {

    private CopheneticDistances() {
    }

    /**
     * Returns the {@code n x n} cophenetic distance matrix for {@code linkage}, where
     * {@code n = linkage.size() + 1}. Symmetric with a zero diagonal by construction. A
     * fresh array is returned on every call — there is no shared mutable state to defend.
     */
    public static double[][] of(LinkageMatrix linkage) {
        Objects.requireNonNull(linkage, "linkage");
        int n = linkage.size() + 1;
        double[][] result = new double[n][n];

        Map<Integer, List<Integer>> leavesOf = new HashMap<>();
        for (int leaf = 0; leaf < n; leaf++) {
            leavesOf.put(leaf, List.of(leaf));
        }

        List<LinkageStep> rows = linkage.rows();
        for (int i = 0; i < rows.size(); i++) {
            LinkageStep row = rows.get(i);
            List<Integer> left = leavesOf.get(row.idx1());
            List<Integer> right = leavesOf.get(row.idx2());
            for (int p : left) {
                for (int q : right) {
                    result[p][q] = row.mergeDistance();
                    result[q][p] = row.mergeDistance();
                }
            }
            List<Integer> merged = new ArrayList<>(left.size() + right.size());
            merged.addAll(left);
            merged.addAll(right);
            leavesOf.put(n + i, merged);
        }
        return result;
    }
}
