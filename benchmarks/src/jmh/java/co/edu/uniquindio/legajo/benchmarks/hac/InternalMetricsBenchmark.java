package co.edu.uniquindio.legajo.benchmarks.hac;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkSeeds;
import co.edu.uniquindio.legajo.benchmarks.input.SyntheticUnitVectors;
import co.edu.uniquindio.legajo.clustering.ClusterAssignment;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LanceWilliamsEngine;
import co.edu.uniquindio.legajo.clustering.LinkageCut;
import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.evaluation.DaviesBouldin;
import co.edu.uniquindio.legajo.evaluation.MeanSilhouette;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.TimeUnit;

/**
 * Internal-metrics complexity curves at a fixed {@code k}: mean silhouette (O(n^2) per k) and
 * Davies-Bouldin (O(n·d + k^2·d) per k, dominated by the O(n·d) term for a fixed small k),
 * each measured at n ∈ {5, 10, 20, 40, 80} synthetic unit vectors.
 *
 * <p>{@code k = min(4, n-1)} is the fixed reference {@code k} used across the evaluation
 * metrics, reused here so the fixed cut is never arbitrary; the cluster assignment comes
 * from single linkage agglomeration over the same synthetic vectors (the choice of criterion
 * does not change either metric's asymptotic cost, only the specific partition it happens
 * to measure).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class InternalMetricsBenchmark {

    private static final int VECTOR_DIMENSION = 128;

    @Param({"5", "10", "20", "40", "80"})
    public int n;

    private DistanceMatrix distanceMatrix;
    private List<List<Double>> vectors;
    private ClusterAssignment assignment;

    @Setup(Level.Trial)
    public void setUp() {
        vectors = SyntheticUnitVectors.generate(n, VECTOR_DIMENSION, BenchmarkSeeds.HAC_VECTORS);
        distanceMatrix = DistanceMatrix.cosineDistance(vectors);
        LinkageMatrix linkage = new LanceWilliamsEngine().agglomerate(distanceMatrix, new SingleLinkage());
        int k = Math.min(4, n - 1);
        assignment = LinkageCut.cut(linkage, k);
    }

    @Benchmark
    public double meanSilhouette() {
        return MeanSilhouette.of(assignment, distanceMatrix);
    }

    @Benchmark
    public OptionalDouble daviesBouldin() {
        return DaviesBouldin.of(assignment, vectors);
    }
}
