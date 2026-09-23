package co.edu.uniquindio.legajo.benchmarks.hac;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkSeeds;
import co.edu.uniquindio.legajo.benchmarks.input.SyntheticDistanceMatrices;
import co.edu.uniquindio.legajo.clustering.AverageLinkage;
import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LanceWilliamsEngine;
import co.edu.uniquindio.legajo.clustering.LinkageCriterion;
import co.edu.uniquindio.legajo.clustering.LinkageMatrix;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.clustering.WardLinkage;
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

import java.util.concurrent.TimeUnit;

/**
 * {@link LanceWilliamsEngine} agglomeration complexity curve (TRD §6.4: naive O(n^3) time /
 * O(n^2) space, independent of which of the four criteria is fusing), one data point per
 * {@code (n, criterion)} pair against a synthetic {@code n x n} distance matrix
 * ({@link SyntheticDistanceMatrices}). Ward runs against {@code D_w = 2*D}
 * ({@link DistanceMatrix#wardBase()}), exactly as RF2 requires (TRD §6.4); the other three
 * criteria run against {@code D} directly.
 *
 * <p>The synthetic vectors' dimension (128) is arbitrary and does not affect the engine's
 * complexity, which is driven by {@code n} alone (TRD §6.4) — {@code cosineDistance} itself
 * runs once in {@code @Setup}, outside the measured operation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class LanceWilliamsBenchmark {

    private static final int VECTOR_DIMENSION = 128;

    @Param({"5", "10", "20", "40", "80"})
    public int n;

    @Param({"single", "complete", "average", "ward"})
    public String criterion;

    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();
    private DistanceMatrix distanceMatrix;
    private LinkageCriterion linkageCriterion;

    @Setup(Level.Trial)
    public void setUp() {
        DistanceMatrix cosineDistance =
                SyntheticDistanceMatrices.cosineDistanceOf(n, VECTOR_DIMENSION, BenchmarkSeeds.HAC_VECTORS);
        linkageCriterion = switch (criterion) {
            case "single" -> new SingleLinkage();
            case "complete" -> new CompleteLinkage();
            case "average" -> new AverageLinkage();
            case "ward" -> new WardLinkage();
            default -> throw new IllegalStateException("unknown criterion: " + criterion);
        };
        distanceMatrix = "ward".equals(criterion) ? cosineDistance.wardBase() : cosineDistance;
    }

    @Benchmark
    public LinkageMatrix agglomerate() {
        return engine.agglomerate(distanceMatrix, linkageCriterion);
    }
}
