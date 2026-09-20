package co.edu.uniquindio.legajo.clustering;

import java.util.List;
import java.util.Objects;

/**
 * The result of cutting a {@link LinkageMatrix} at a fixed {@code k} (TRD §6.4: "corte solo
 * por k"): one cluster label per original observation, 0-based and contiguous over
 * {@code [0, k-1]}. {@link LinkageCut} is the only intended producer, but this record
 * re-validates its own shape rather than trusting its caller — the house rule this
 * package's other validated value types ({@link LinkageStep}, {@link LinkageMatrix}) already
 * follow.
 */
public record ClusterAssignment(List<Integer> labels, int k) {

    public ClusterAssignment {
        Objects.requireNonNull(labels, "labels");
        labels = List.copyOf(labels);
        if (labels.isEmpty()) {
            throw new IllegalArgumentException("labels must not be empty");
        }
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1, was " + k);
        }
        for (int label : labels) {
            if (label < 0 || label >= k) {
                throw new IllegalArgumentException(
                        "every label must be in [0, k) (k=%d), found %d".formatted(k, label));
            }
        }
    }

    /** The number of original observations this assignment covers. */
    public int size() {
        return labels.size();
    }
}
