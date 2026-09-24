package co.edu.uniquindio.legajo.infrastructure.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hand-written mean pooling for MiniLM: pooling must not be delegated to a library. The
 * fixed pipeline is to mean-pool each window's token embeddings first, then mean-pool the
 * resulting per-window vectors. Pure arithmetic over plain arrays/lists — {@link
 * MiniLmEmbedder} is the only caller that ever supplies real token embeddings from an ONNX
 * session; every test here fabricates its inputs.
 */
final class MiniLmPooling {

    private MiniLmPooling() {
    }

    /**
     * Mean-pools {@code tokenEmbeddings[token][dimension]} over the token dimension,
     * counting only tokens where {@code attentionMask[token] != 0} (real tokens, never
     * padding).
     */
    static List<Double> meanPoolTokens(float[][] tokenEmbeddings, long[] attentionMask) {
        Objects.requireNonNull(tokenEmbeddings, "tokenEmbeddings");
        Objects.requireNonNull(attentionMask, "attentionMask");
        if (tokenEmbeddings.length != attentionMask.length) {
            throw new IllegalArgumentException(
                    "tokenEmbeddings and attentionMask must have the same length, was %d and %d"
                            .formatted(tokenEmbeddings.length, attentionMask.length));
        }
        if (tokenEmbeddings.length == 0) {
            throw new IllegalArgumentException("tokenEmbeddings must not be empty");
        }

        int dimension = tokenEmbeddings[0].length;
        double[] sum = new double[dimension];
        long attendedCount = 0;
        for (int token = 0; token < tokenEmbeddings.length; token++) {
            if (attentionMask[token] == 0) {
                continue;
            }
            attendedCount++;
            for (int d = 0; d < dimension; d++) {
                sum[d] += tokenEmbeddings[token][d];
            }
        }
        if (attendedCount == 0) {
            throw new IllegalArgumentException("attentionMask has no attended (non-zero) token to pool over");
        }

        List<Double> mean = new ArrayList<>(dimension);
        for (double component : sum) {
            mean.add(component / attendedCount);
        }
        return mean;
    }

    /** Mean-pools {@code windowVectors} (one per window of the same abstract) into one vector. */
    static List<Double> meanPoolWindows(List<List<Double>> windowVectors) {
        Objects.requireNonNull(windowVectors, "windowVectors");
        if (windowVectors.isEmpty()) {
            throw new IllegalArgumentException("windowVectors must not be empty");
        }

        int dimension = windowVectors.getFirst().size();
        double[] sum = new double[dimension];
        for (List<Double> vector : windowVectors) {
            if (vector.size() != dimension) {
                throw new IllegalArgumentException(
                        "every window vector must have the same dimension (%d), found one with %d"
                                .formatted(dimension, vector.size()));
            }
            for (int d = 0; d < dimension; d++) {
                sum[d] += vector.get(d);
            }
        }

        List<Double> mean = new ArrayList<>(dimension);
        for (double component : sum) {
            mean.add(component / windowVectors.size());
        }
        return mean;
    }
}
