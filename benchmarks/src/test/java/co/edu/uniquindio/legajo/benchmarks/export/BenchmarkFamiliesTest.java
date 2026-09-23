package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the benchmark-to-family/size-key/theoretical-exponent mapping (TRD
 * §6.3/§6.4/§6.5, TAC-18). Written before {@link BenchmarkFamilies} exists
 * (odd/tasks/jmh-benchmarks.md, task J2: strict TDD).
 */
class BenchmarkFamiliesTest {

    @Test
    void mapsAPairwiseBenchmarkToItsFamilyAndSizeKey() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute";
        Map<String, String> params = Map.of("length", "400");

        BenchmarkFamily family = BenchmarkFamilies.classify(benchmark, params);

        assertThat(family.family()).isEqualTo("levenshtein");
        assertThat(family.sizeParameterKey()).isEqualTo("length");
        assertThat(family.size()).isEqualTo(400.0);
        assertThat(family.theoreticalExponent()).contains(2.0);
    }

    @Test
    void appendsTheCriterionToTheHacFamilyLabel() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.hac.LanceWilliamsBenchmark.agglomerate";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("n", "40");
        params.put("criterion", "ward");

        BenchmarkFamily family = BenchmarkFamilies.classify(benchmark, params);

        assertThat(family.family()).isEqualTo("hac-ward");
        assertThat(family.sizeParameterKey()).isEqualTo("n");
        assertThat(family.size()).isEqualTo(40.0);
        assertThat(family.theoreticalExponent()).contains(3.0);
    }

    @Test
    void appendsTheAlgorithmIdToTheSloFamilyLabel() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.slo.ClassicPairwiseSloBenchmark.allPairsForAlgorithm";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("n", "20");
        params.put("algorithmId", "jaccard");

        BenchmarkFamily family = BenchmarkFamilies.classify(benchmark, params);

        assertThat(family.family()).isEqualTo("slo-classic-jaccard");
        assertThat(family.sizeParameterKey()).isEqualTo("n");
        assertThat(family.size()).isEqualTo(20.0);
        // A fixed-n SLO measurement is not a curve: it has no theoretical exponent to slope against.
        assertThat(family.theoreticalExponent()).isEmpty();
    }

    @Test
    void distinguishesTheTwoEmbeddingPrimitivesByMethodName() {
        String dotProduct = "co.edu.uniquindio.legajo.benchmarks.embedding.EmbeddingPrimitiveBenchmark.dotProduct";
        String euclidean =
                "co.edu.uniquindio.legajo.benchmarks.embedding.EmbeddingPrimitiveBenchmark.euclideanSumOfSquaredDifferences";
        Map<String, String> params = Map.of("dimension", "1536");

        BenchmarkFamily dotProductFamily = BenchmarkFamilies.classify(dotProduct, params);
        BenchmarkFamily euclideanFamily = BenchmarkFamilies.classify(euclidean, params);

        assertThat(dotProductFamily.family()).isEqualTo("embedding-dot-product");
        assertThat(euclideanFamily.family()).isEqualTo("embedding-euclidean-sum-squared");
        assertThat(dotProductFamily.sizeParameterKey()).isEqualTo("dimension");
        assertThat(dotProductFamily.theoreticalExponent()).contains(1.0);
    }

    @Test
    void distinguishesTheTwoInternalMetricsByMethodName() {
        String silhouette = "co.edu.uniquindio.legajo.benchmarks.hac.InternalMetricsBenchmark.meanSilhouette";
        String daviesBouldin = "co.edu.uniquindio.legajo.benchmarks.hac.InternalMetricsBenchmark.daviesBouldin";
        Map<String, String> params = Map.of("n", "80");

        BenchmarkFamily silhouetteFamily = BenchmarkFamilies.classify(silhouette, params);
        BenchmarkFamily daviesBouldinFamily = BenchmarkFamilies.classify(daviesBouldin, params);

        assertThat(silhouetteFamily.family()).isEqualTo("mean-silhouette");
        assertThat(silhouetteFamily.theoreticalExponent()).contains(2.0);
        assertThat(daviesBouldinFamily.family()).isEqualTo("davies-bouldin");
        assertThat(daviesBouldinFamily.theoreticalExponent()).contains(1.0);
    }

    @Test
    void theoreticalExponentIsEmptyForAnUnrecognizedFamily() {
        Optional<Double> exponent = BenchmarkFamilies.theoreticalExponentOf("some-unknown-family");

        assertThat(exponent).isEmpty();
    }

    @Test
    void buildsTheFamilySuffixFromNonSizeParamsInSortedKeyOrderRegardlessOfMapIterationOrder() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.hac.LanceWilliamsBenchmark.agglomerate";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("zetaParam", "z-value");
        params.put("n", "40");
        params.put("alphaParam", "a-value");

        BenchmarkFamily family = BenchmarkFamilies.classify(benchmark, params);

        // Sorted by key ("alphaParam" < "zetaParam"), never map insertion or iteration order.
        assertThat(family.family()).isEqualTo("hac-a-value-z-value");
    }

    @Test
    void rejectsMoreThanOneRecognizedSizeParameter() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute";
        Map<String, String> params = new LinkedHashMap<>();
        params.put("length", "50");
        params.put("n", "20");

        // R2-ambiguous-size-test-trivial-assertion / R3-004: assert on the exact listed keys
        // this branch produces, not on a bare "n" that would match almost any message.
        assertThatThrownBy(() -> BenchmarkFamilies.classify(benchmark, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found [length, n]");
    }

    @Test
    void rejectsANonNumericSizeWithAClearMessageInsteadOfAbortingTheWholeExport() {
        String benchmark = "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute";
        Map<String, String> params = Map.of("length", "not-a-number");

        assertThatThrownBy(() -> BenchmarkFamilies.classify(benchmark, params))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("length")
                .hasMessageContaining("not-a-number")
                .hasMessageContaining(benchmark);
    }

    @Test
    void classifyAllReturnsOneClassifiedResultPerRecordInOrder() {
        List<JmhResultRecord> records = List.of(
                recordWith("co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                        Map.of("length", "50")),
                recordWith("co.edu.uniquindio.legajo.benchmarks.pairwise.JaccardBenchmark.pairwiseCompute",
                        Map.of("length", "100")));

        List<ClassifiedBenchmarkResult> classified = BenchmarkFamilies.classifyAll(records);

        assertThat(classified).hasSize(2);
        assertThat(classified.get(0).family().family()).isEqualTo("levenshtein");
        assertThat(classified.get(1).family().family()).isEqualTo("jaccard");
    }

    @Test
    void classifyAllFailsAtomicallyListingEveryUnclassifiableRecordAndReason() {
        List<JmhResultRecord> records = List.of(
                recordWith("co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                        Map.of("length", "not-a-number")),
                recordWith("co.edu.uniquindio.legajo.benchmarks.pairwise.JaccardBenchmark.pairwiseCompute",
                        Map.of("length", "also-not-numeric")));

        assertThatThrownBy(() -> BenchmarkFamilies.classifyAll(records))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LevenshteinBenchmark.pairwiseCompute")
                .hasMessageContaining("not-a-number")
                .hasMessageContaining("JaccardBenchmark.pairwiseCompute")
                .hasMessageContaining("also-not-numeric");
    }

    private static JmhResultRecord recordWith(String benchmark, Map<String, String> params) {
        return new JmhResultRecord(benchmark, params, 1.0, 0.0, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
    }
}
