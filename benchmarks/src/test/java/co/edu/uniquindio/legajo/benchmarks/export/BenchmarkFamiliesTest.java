package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
