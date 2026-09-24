package co.edu.uniquindio.legajo.benchmarks.export;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Classifies one JMH result (its fully qualified {@code Class.method} benchmark name plus
 * its {@code @Param} map) into a {@link BenchmarkFamily}, and holds this harness's
 * theoretical complexity exponents for the pairwise classic algorithms, the HAC engine, and
 * the two internal metrics — every exponent below is a documented, not invented, value.
 *
 * <p>A benchmark class/method pair not in {@link #BASE_LABELS} falls back to
 * {@code SimpleClassName.methodName} as its own label, so a future benchmark this table has
 * not been updated for still exports a row (with no theoretical exponent) instead of failing
 * the whole export.
 */
public final class BenchmarkFamilies {

    /** JMH {@code @Param} names this harness uses for a curve's varying size, in lookup order. */
    private static final List<String> SIZE_PARAM_KEYS = List.of("length", "n", "dimension");

    private static final Map<String, String> BASE_LABELS = Map.ofEntries(
            Map.entry("LevenshteinBenchmark#pairwiseCompute", "levenshtein"),
            Map.entry("NeedlemanWunschBenchmark#pairwiseCompute", "needleman-wunsch"),
            Map.entry("JaccardBenchmark#pairwiseCompute", "jaccard"),
            Map.entry("TfIdfCosineBenchmark#pairwiseCompute", "tfidf-cosine"),
            Map.entry("LanceWilliamsBenchmark#agglomerate", "hac"),
            Map.entry("InternalMetricsBenchmark#meanSilhouette", "mean-silhouette"),
            Map.entry("InternalMetricsBenchmark#daviesBouldin", "davies-bouldin"),
            Map.entry("EmbeddingPrimitiveBenchmark#dotProduct", "embedding-dot-product"),
            Map.entry("EmbeddingPrimitiveBenchmark#euclideanSumOfSquaredDifferences", "embedding-euclidean-sum-squared"),
            Map.entry("ClassicPairwiseSloBenchmark#allPairsForAlgorithm", "slo-classic"),
            Map.entry("ClusteringSloBenchmark#allFourLinkages", "slo-clustering"));

    /**
     * Documented theoretical exponents, keyed by the base label (before any categorical
     * suffix, e.g. {@code "hac"}, not {@code "hac-ward"} — the same exponent applies to every
     * criterion). {@code levenshtein}/{@code needleman-wunsch}: O(L²).
     * {@code jaccard}/{@code tfidf-cosine}: O(L) per pair (TF-IDF's one-time
     * O(N·L) corpus indexing is outside the measured operation, see
     * {@code TfIdfCosineBenchmark}'s Javadoc). {@code hac}: O(n³).
     * {@code mean-silhouette}: O(n²) per k. {@code davies-bouldin}: O(n·d + k²·d)
     * per k, dominated by the O(n·d) term for the fixed small k and d this harness uses.
     * The two embedding primitives: O(d). The {@code slo-*} families are
     * fixed-n SLO measurements, not curves, and are deliberately absent here.
     */
    private static final Map<String, Double> THEORETICAL_EXPONENTS = Map.ofEntries(
            Map.entry("levenshtein", 2.0),
            Map.entry("needleman-wunsch", 2.0),
            Map.entry("jaccard", 1.0),
            Map.entry("tfidf-cosine", 1.0),
            Map.entry("hac", 3.0),
            Map.entry("mean-silhouette", 2.0),
            Map.entry("davies-bouldin", 1.0),
            Map.entry("embedding-dot-product", 1.0),
            Map.entry("embedding-euclidean-sum-squared", 1.0));

    private BenchmarkFamilies() {
    }

    /**
     * Classifies one JMH result. {@code params} must contain exactly one of
     * {@link #SIZE_PARAM_KEYS} (more than one is rejected as ambiguous harness metadata,
     * never silently resolved by {@link #SIZE_PARAM_KEYS}' lookup order); every other entry
     * (e.g. {@code criterion}, {@code algorithmId}) is appended to the base label, sorted by
     * key, to distinguish curves that share one benchmark class deterministically regardless
     * of {@code params}' own iteration order.
     *
     * <p>A non-numeric size value fails with an {@link IllegalArgumentException} naming the
     * benchmark, the offending parameter key and its raw value, rather than defaulting to a
     * misleading number. This method itself never drops anything silently — it always throws
     * on a record it cannot classify — but a caller exporting many records that calls this
     * method directly in a loop and catches each failure individually would end up silently
     * skipping unclassifiable rows one at a time. Callers exporting many records should use
     * {@link #classifyAll} instead: it attempts every record, then fails the whole export
     * atomically with every failure reported together, so no unclassifiable row is ever
     * dropped without being reported.
     */
    public static BenchmarkFamily classify(String benchmark, Map<String, String> params) {
        Objects.requireNonNull(benchmark, "benchmark");
        Objects.requireNonNull(params, "params");

        String[] segments = benchmark.split("\\.");
        if (segments.length < 2) {
            throw new IllegalArgumentException(
                    "benchmark must be a fully qualified Class.method name, was " + benchmark);
        }
        String method = segments[segments.length - 1];
        String simpleClass = segments[segments.length - 2];
        String baseLabel = BASE_LABELS.getOrDefault(simpleClass + "#" + method, simpleClass + "." + method);

        List<String> presentSizeKeys = SIZE_PARAM_KEYS.stream().filter(params::containsKey).toList();
        if (presentSizeKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "no recognized size parameter (%s) in %s".formatted(SIZE_PARAM_KEYS, params));
        }
        if (presentSizeKeys.size() > 1) {
            throw new IllegalArgumentException(
                    "expected exactly one size parameter among %s for benchmark '%s', found %s in %s"
                            .formatted(SIZE_PARAM_KEYS, benchmark, presentSizeKeys, params));
        }
        String sizeKey = presentSizeKeys.get(0);
        String rawSize = params.get(sizeKey);
        double size;
        try {
            size = Double.parseDouble(rawSize);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "size parameter '%s' for benchmark '%s' is not numeric: '%s'"
                            .formatted(sizeKey, benchmark, rawSize), e);
        }

        String suffix = params.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(sizeKey))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.joining("-"));
        String family = suffix.isEmpty() ? baseLabel : baseLabel + "-" + suffix;

        return new BenchmarkFamily(family, sizeKey, size, theoreticalExponentOf(baseLabel));
    }

    /** The documented theoretical exponent for a base label (or family), if this table has one. */
    public static Optional<Double> theoreticalExponentOf(String baseLabelOrFamily) {
        return Optional.ofNullable(THEORETICAL_EXPONENTS.get(baseLabelOrFamily));
    }

    /**
     * Classifies every record, or fails the whole export atomically. The versioned CSVs back
     * the technical documentation, so an export missing rows it silently could not
     * classify is worse than no export at all: unlike {@link #classify}, this never drops a
     * record on its own. Every record is attempted (a first failure never short-circuits the
     * rest), and if any record fails, {@link IllegalStateException} lists every one of them —
     * the benchmark name and the classification failure reason — before either CSV writer
     * ({@link JmhResultsCsvWriter}, {@link SlopesCsvWriter}) ever runs, so no partial CSV is
     * ever written.
     */
    public static List<ClassifiedBenchmarkResult> classifyAll(List<JmhResultRecord> records) {
        List<ClassifiedBenchmarkResult> classified = new ArrayList<>(records.size());
        List<String> failures = new ArrayList<>();
        for (JmhResultRecord record : records) {
            try {
                classified.add(new ClassifiedBenchmarkResult(record, classify(record.benchmark(), record.params())));
            } catch (IllegalArgumentException e) {
                failures.add("'" + record.benchmark() + "': " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException(
                    "refusing to export: %d benchmark result(s) failed classification:\n  - %s"
                            .formatted(failures.size(), String.join("\n  - ", failures)));
        }
        return classified;
    }
}
