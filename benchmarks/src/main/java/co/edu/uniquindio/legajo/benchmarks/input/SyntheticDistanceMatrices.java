package co.edu.uniquindio.legajo.benchmarks.input;

import co.edu.uniquindio.legajo.clustering.DistanceMatrix;

import java.util.List;

/**
 * Builds the synthetic {@code n x n} distance matrix one HAC-curve benchmark data point
 * needs, following the fixed performance-test protocol: {@code n} deterministic unit vectors
 * ({@link SyntheticUnitVectors}), turned into {@code D = 1 - cos(V)} the same way the real
 * clustering pipeline does ({@link DistanceMatrix#cosineDistance(List)} is the domain's only
 * public way to build one).
 */
public final class SyntheticDistanceMatrices {

    private SyntheticDistanceMatrices() {
    }

    /** The {@code n x n} cosine distance matrix over {@code n} synthetic unit vectors. */
    public static DistanceMatrix cosineDistanceOf(int n, int dimension, long seed) {
        List<List<Double>> vectors = SyntheticUnitVectors.generate(n, dimension, seed);
        return DistanceMatrix.cosineDistance(vectors);
    }
}
