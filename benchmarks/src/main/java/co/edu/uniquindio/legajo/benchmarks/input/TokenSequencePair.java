package co.edu.uniquindio.legajo.benchmarks.input;

import java.util.List;
import java.util.Objects;

/**
 * Two deterministic synthetic token sequences of the same length, built by
 * {@link SyntheticTokenSequences} for one pairwise-curve benchmark data point, following the
 * fixed performance-test protocol.
 */
public record TokenSequencePair(List<String> sequenceA, List<String> sequenceB) {

    public TokenSequencePair {
        Objects.requireNonNull(sequenceA, "sequenceA");
        Objects.requireNonNull(sequenceB, "sequenceB");
        sequenceA = List.copyOf(sequenceA);
        sequenceB = List.copyOf(sequenceB);
    }
}
