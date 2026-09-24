package co.edu.uniquindio.legajo.benchmarks.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds deterministic, L2-normalized synthetic vectors for the HAC-curve benchmarks
 * (synthetic unit vectors with n ∈ {5, 10, 20, 40, 80}, per the fixed performance-test
 * protocol) and for the embedding-primitive benchmark (one timing measurement at d = 384 and
 * one at d = 1536).
 *
 * <p>Components are drawn from a single {@link Random} seeded once, so a given {@code (count,
 * dimension, seed)} triple always yields the same vectors, and each vector is then
 * hand-normalized to unit length exactly the way the domain's own {@code EmbeddingVector} and
 * {@code DistanceMatrix} expect their inputs: the unit-norm invariant both classes rely on.
 */
public final class SyntheticUnitVectors {

    private SyntheticUnitVectors() {
    }

    /** Generates {@code count} L2-normalized vectors of {@code dimension} components each. */
    public static List<List<Double>> generate(int count, int dimension, long seed) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, was " + count);
        }
        if (dimension < 1) {
            throw new IllegalArgumentException("dimension must be at least 1, was " + dimension);
        }

        Random random = new Random(seed);
        List<List<Double>> vectors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            vectors.add(oneUnitVector(random, dimension, i));
        }
        return List.copyOf(vectors);
    }

    private static List<Double> oneUnitVector(Random random, int dimension, int index) {
        double[] raw = new double[dimension];
        double sumOfSquares = 0.0;
        for (int d = 0; d < dimension; d++) {
            double component = random.nextDouble() * 2.0 - 1.0;
            raw[d] = component;
            sumOfSquares += component * component;
        }
        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0) {
            // Astronomically unlikely with continuous doubles, but this is a deterministic
            // fixture generator: fail loudly rather than ever hand a caller a NaN-producing
            // all-zero vector, mirroring the domain's own fail-closed convention
            // (EmbeddingVector#normalize).
            throw new IllegalStateException(
                    "degenerate all-zero synthetic vector at index " + index + "; retry with a different seed");
        }
        List<Double> unit = new ArrayList<>(dimension);
        for (double component : raw) {
            unit.add(component / norm);
        }
        return unit;
    }
}
