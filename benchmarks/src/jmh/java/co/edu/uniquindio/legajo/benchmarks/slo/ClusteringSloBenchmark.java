package co.edu.uniquindio.legajo.benchmarks.slo;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkCorpus;
import co.edu.uniquindio.legajo.clustering.AverageLinkage;
import co.edu.uniquindio.legajo.clustering.CompleteLinkage;
import co.edu.uniquindio.legajo.clustering.DistanceMatrix;
import co.edu.uniquindio.legajo.clustering.LanceWilliamsEngine;
import co.edu.uniquindio.legajo.clustering.SingleLinkage;
import co.edu.uniquindio.legajo.clustering.WardLinkage;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusIndex;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusVectors;
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
import org.openjdk.jmh.infra.Blackhole;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The clustering SLO (service-level objective) benchmark: one operation is single, complete,
 * average and Ward agglomeration from the real reference corpus's {@code tfidf-cosine}
 * distance matrix (the default distance representation this clustering engine uses) at
 * n = 20, precomputed once in {@code @Setup} so the measured operation is only the four
 * agglomerations, matching the benchmarked operation starting from cached distance matrices
 * and vectors rather than recomputing them.
 *
 * <p>{@code n} is a JMH {@code @Param} for the same self-documenting CSV reason as
 * {@link ClassicPairwiseSloBenchmark}; {@code @Setup} asserts it against the loaded corpus.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class ClusteringSloBenchmark {

    @Param({"20"})
    public int n;

    private final LanceWilliamsEngine engine = new LanceWilliamsEngine();
    private final SingleLinkage single = new SingleLinkage();
    private final CompleteLinkage complete = new CompleteLinkage();
    private final AverageLinkage average = new AverageLinkage();
    private final WardLinkage ward = new WardLinkage();

    private DistanceMatrix distanceMatrix;
    private DistanceMatrix wardBase;

    @Setup(Level.Trial)
    public void setUp() {
        Corpus corpus = BenchmarkCorpus.load();
        if (corpus.documents().size() != n) {
            throw new IllegalStateException(
                    "expected the reference corpus to have %d documents, was %d"
                            .formatted(n, corpus.documents().size()));
        }
        List<List<String>> tokenStreams = BenchmarkCorpus.preprocessedTokenStreams(corpus);
        TfIdfCorpusIndex index = TfIdfCorpusIndex.from(tokenStreams);
        List<List<Double>> vectors = TfIdfCorpusVectors.vectorsOf(tokenStreams, index);

        distanceMatrix = DistanceMatrix.cosineDistance(vectors);
        wardBase = distanceMatrix.wardBase();
    }

    @Benchmark
    public void allFourLinkages(Blackhole blackhole) {
        blackhole.consume(engine.agglomerate(distanceMatrix, single));
        blackhole.consume(engine.agglomerate(distanceMatrix, complete));
        blackhole.consume(engine.agglomerate(distanceMatrix, average));
        blackhole.consume(engine.agglomerate(wardBase, ward));
    }
}
