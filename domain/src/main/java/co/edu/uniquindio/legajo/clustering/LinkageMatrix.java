package co.edu.uniquindio.legajo.clustering;

import java.util.List;
import java.util.Objects;

/**
 * The full linkage matrix for one criterion's run: n-1 {@link LinkageStep} rows
 * in merge order (an empty list for the degenerate n=1 case, where there is nothing to
 * merge). {@link LanceWilliamsEngine} is the intended producer, but this type
 * re-validates the monotonicity invariant itself (merge distances must be monotone
 * non-decreasing) rather than trusting its caller — the house rule this codebase's other
 * validated value types (e.g. {@link DistanceMatrix}) already follow.
 */
public record LinkageMatrix(List<LinkageStep> rows) {

    private static final double TOLERANCE = 1e-9;

    public LinkageMatrix {
        Objects.requireNonNull(rows, "rows");
        rows = List.copyOf(rows);
        for (int i = 1; i < rows.size(); i++) {
            double previous = rows.get(i - 1).mergeDistance();
            double current = rows.get(i).mergeDistance();
            if (current < previous - TOLERANCE) {
                throw new IllegalArgumentException(
                        ("merge distances must be monotone non-decreasing: row %d (%.15f) "
                                + "is smaller than row %d (%.15f)").formatted(i, current, i - 1, previous));
            }
        }
    }

    /** The number of rows, i.e. the number of merges performed (n-1 for n original observations). */
    public int size() {
        return rows.size();
    }
}
