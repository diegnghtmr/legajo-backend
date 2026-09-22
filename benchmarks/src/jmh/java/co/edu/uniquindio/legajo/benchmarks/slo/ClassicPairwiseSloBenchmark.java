package co.edu.uniquindio.legajo.benchmarks.slo;

import co.edu.uniquindio.legajo.benchmarks.input.BenchmarkCorpus;
import co.edu.uniquindio.legajo.corpus.Corpus;
import co.edu.uniquindio.legajo.similarity.Jaccard;
import co.edu.uniquindio.legajo.similarity.Levenshtein;
import co.edu.uniquindio.legajo.similarity.NeedlemanWunsch;
import co.edu.uniquindio.legajo.similarity.SimilarityAlgorithm;
import co.edu.uniquindio.legajo.similarity.SimilarityContext;
import co.edu.uniquindio.legajo.similarity.SimilarityInput;
import co.edu.uniquindio.legajo.similarity.TfIdfCorpusIndex;
import co.edu.uniquindio.legajo.similarity.TfIdfCosine;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * NFR-QA-01's SLO benchmark: one operation is all {@code C(n,2)} classic pairwise
 * comparisons of the real reference corpus (190 comparisons at n = 20) for one algorithm,
 * with the similarity cache off — met here by never involving a cache at all: this class
 * calls each {@link SimilarityAlgorithm#compute} directly, the same way the pairwise-curve
 * benchmarks do, just over the real corpus instead of a synthetic sequence and over every
 * pair instead of one.
 *
 * <p>{@code n} is declared as a JMH {@code @Param} purely so the CSV export (J2) carries an
 * explicit, self-documenting size column; {@code @Setup} asserts the loaded corpus actually
 * has {@code n} documents and fails loudly otherwise, so this never silently reports a wrong
 * size if the reference corpus is ever resized.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@State(Scope.Benchmark)
public class ClassicPairwiseSloBenchmark {

    @Param({"20"})
    public int n;

    @Param({"levenshtein", "needleman-wunsch", "jaccard", "tfidf-cosine"})
    public String algorithmId;

    private SimilarityAlgorithm algorithm;
    private List<SimilarityInput> inputs;
    private SimilarityContext context;

    @Setup(Level.Trial)
    public void setUp() {
        Corpus corpus = BenchmarkCorpus.load();
        if (corpus.documents().size() != n) {
            throw new IllegalStateException(
                    "expected the reference corpus to have %d documents, was %d"
                            .formatted(n, corpus.documents().size()));
        }
        List<List<String>> tokenStreams = BenchmarkCorpus.preprocessedTokenStreams(corpus);

        inputs = new ArrayList<>(tokenStreams.size());
        for (List<String> tokens : tokenStreams) {
            inputs.add(new SimilarityInput("", tokens));
        }

        algorithm = switch (algorithmId) {
            case "levenshtein" -> new Levenshtein();
            case "needleman-wunsch" -> new NeedlemanWunsch();
            case "jaccard" -> new Jaccard();
            case "tfidf-cosine" -> new TfIdfCosine();
            default -> throw new IllegalStateException("unknown algorithmId: " + algorithmId);
        };
        context = "tfidf-cosine".equals(algorithmId)
                ? SimilarityContext.withTfIdfIndex(TfIdfCorpusIndex.from(tokenStreams))
                : SimilarityContext.EMPTY;
    }

    @Benchmark
    public void allPairsForAlgorithm(Blackhole blackhole) {
        int size = inputs.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                blackhole.consume(algorithm.compute(inputs.get(i), inputs.get(j), context));
            }
        }
    }
}
