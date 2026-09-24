package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@code slopes.csv} writer (the empirical log-log slope of each curve
 * against its documented theoretical exponent). Written before {@link SlopesCsvWriter} exists
 * (strict TDD).
 *
 * <p>Every {@link ClassifiedBenchmarkResult} this writer receives is already classified
 * ({@link BenchmarkFamilies#classifyAll}, called by {@link JmhExportCli} before either writer
 * runs): an unclassifiable record can no longer reach this writer, it aborts the whole export
 * before any file is written (see {@code JmhExportCliTest}), so this writer itself has nothing
 * left to skip.
 */
class SlopesCsvWriterTest {

    private static final List<Integer> LENGTHS = List.of(50, 100, 200, 400, 800);

    @Test
    void writesTheColumnHeaderFirst(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("slopes.csv");

        SlopesCsvWriter.write(output, classifyAll(quadraticLevenshteinRecords()));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines.get(0)).isEqualTo("family,points,empiricalSlope,theoreticalExponent");
    }

    @Test
    void reportsTheKnownSlopeForAKnownQuadraticCurve(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("slopes.csv");

        SlopesCsvWriter.write(output, classifyAll(quadraticLevenshteinRecords()));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).isEqualTo("levenshtein,5,2.000000,2.000000");
    }

    @Test
    void excludesAFixedNSloFamilyWithNoTheoreticalExponent(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("slopes.csv");
        List<JmhResultRecord> records = new ArrayList<>(quadraticLevenshteinRecords());
        records.add(new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.slo.ClassicPairwiseSloBenchmark.allPairsForAlgorithm",
                Map.of("n", "20", "algorithmId", "jaccard"), 1.0, 0.0, "ms/op", "avgt", 1, 3, "1 s", 5, "1 s"));

        SlopesCsvWriter.write(output, classifyAll(records));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(2);
        assertThat(lines).noneMatch(line -> line.startsWith("slo-classic"));
    }

    @Test
    void excludesAFamilyWithFewerThanTwoPoints(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("slopes.csv");
        List<JmhResultRecord> records = List.of(new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.JaccardBenchmark.pairwiseCompute",
                Map.of("length", "50"), 3.0, 0.1, "us/op", "avgt", 1, 3, "1 s", 5, "1 s"));

        SlopesCsvWriter.write(output, classifyAll(records));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(1);
    }

    @Test
    void excludesAFamilyWhoseTwoPointsShareTheSameSizeInsteadOfThrowing(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("slopes.csv");
        List<JmhResultRecord> records = List.of(
                new JmhResultRecord(
                        "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                        Map.of("length", "50"), 10.0, 0.0, "us/op", "avgt", 1, 3, "1 s", 5, "1 s"),
                new JmhResultRecord(
                        "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                        Map.of("length", "50"), 12.0, 0.0, "us/op", "avgt", 1, 3, "1 s", 5, "1 s"));

        SlopesCsvWriter.write(output, classifyAll(records));

        List<String> lines = Files.readAllLines(output);
        assertThat(lines).hasSize(1);
    }

    private static List<ClassifiedBenchmarkResult> classifyAll(List<JmhResultRecord> records) {
        return BenchmarkFamilies.classifyAll(records);
    }

    private static List<JmhResultRecord> quadraticLevenshteinRecords() {
        List<JmhResultRecord> records = new ArrayList<>();
        for (int length : LENGTHS) {
            records.add(new JmhResultRecord(
                    "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                    Map.of("length", String.valueOf(length)), (double) length * length, 0.0, "us/op", "avgt", 1, 3,
                    "1 s", 5, "1 s"));
        }
        return records;
    }
}
