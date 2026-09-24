package co.edu.uniquindio.legajo.clustering;

import co.edu.uniquindio.legajo.similarity.NumericGuards;

/**
 * One row of a linkage matrix: the two
 * cluster ids merged, the distance at which they merged, and the resulting cluster's size
 * in original observations. {@code idx1 < idx2} always, by construction;
 * {@code size} is at least 2 because a merge always combines two
 * non-empty clusters. The cluster this merge creates is not itself a field — {@code n + i}
 * for row {@code i} (0-based) is a fixed convention that {@link LinkageMatrix}'s
 * position in its row list already encodes, so storing it again on every row would be a
 * redundant, independently-mutable copy of the same fact.
 */
public record LinkageStep(int idx1, int idx2, double mergeDistance, int size) {

    public LinkageStep {
        if (idx1 < 0) {
            throw new IllegalArgumentException("idx1 must not be negative, was " + idx1);
        }
        if (idx2 < 0) {
            throw new IllegalArgumentException("idx2 must not be negative, was " + idx2);
        }
        if (idx1 >= idx2) {
            throw new IllegalArgumentException(
                    "idx1 must be strictly less than idx2, was idx1=%d, idx2=%d".formatted(idx1, idx2));
        }
        NumericGuards.requireNonNegativeFinite(mergeDistance, "mergeDistance");
        if (size < 2) {
            throw new IllegalArgumentException(
                    "size must be at least 2 (a merge combines two non-empty clusters), was " + size);
        }
    }
}
