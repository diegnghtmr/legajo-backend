package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@code jmh-results.csv} writer (TRD NFR-QA-10, TAC-18: harness header +
 * {@code benchmark,family,parameter,size,score,error,unit} columns). Written before
 * {@link JmhResultsCsvWriter} exists (odd/tasks/jmh-benchmarks.md, task J2: strict TDD).
 *
 * <p>Every {@link ClassifiedBenchmarkResult} this writer receives is already classified
 * ({@link BenchmarkFamilies#classifyAll}, called by {@link JmhExportCli} before either writer
 * runs): an unclassifiable record can no longer reach this writer, it aborts the whole export
 * before any file is written (see {@code JmhExportCliTest}), so this writer itself has nothing
 * left to skip.
 */
class JmhResultsCsvWriterTest {

    private static final HarnessInfo HARNESS =
            new HarnessInfo("Test CPU", 4, 8_000_000_000L, "Temurin 25", "Linux 6.0 (amd64)", "2026-09-22T00:00:00Z");

    /**
     * The exact lines {@link HarnessInfo#toHeaderLines()} must produce for {@link #HARNESS},
     * spelled out literally instead of calling {@code HARNESS.toHeaderLines()} itself: a test
     * that builds its own expectation from the very method it is testing can never catch a bug
     * in that method's field order or {@code "# harness.<key> = "} formatting, since a wrong
     * implementation and the test's "expected" value would drift together.
     */
    private static final List<String> EXPECTED_HARNESS_HEADER_LINES = List.of(
            "# harness.cpuModel = Test CPU",
            "# harness.logicalCores = 4",
            "# harness.totalRamBytes = 8000000000",
            "# harness.jdk = Temurin 25",
            "# harness.os = Linux 6.0 (amd64)",
            "# harness.utcDate = 2026-09-22T00:00:00Z");

    @Test
    void writesTheHarnessHeaderLinesFirst(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<ClassifiedBenchmarkResult> results = classifyAll(oneRecord());

        JmhResultsCsvWriter.write(output, HARNESS, results);

        List<String> lines = Files.readAllLines(output);
        assertThat(lines.subList(0, EXPECTED_HARNESS_HEADER_LINES.size()))
                .isEqualTo(EXPECTED_HARNESS_HEADER_LINES);
    }

    @Test
    void writesTheColumnHeaderRightAfterTheHarnessLines(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<ClassifiedBenchmarkResult> results = classifyAll(oneRecord());

        JmhResultsCsvWriter.write(output, HARNESS, results);

        List<String> lines = Files.readAllLines(output);
        assertThat(lines.get(EXPECTED_HARNESS_HEADER_LINES.size()))
                .isEqualTo("benchmark,family,parameter,size,score,error,unit");
    }

    @Test
    void writesOneDataRowPerRecord(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("jmh-results.csv");
        List<ClassifiedBenchmarkResult> results = classifyAll(oneRecord(), anotherRecord());

        JmhResultsCsvWriter.write(output, HARNESS, results);

        List<String> lines = Files.readAllLines(output);
        int firstDataLine = EXPECTED_HARNESS_HEADER_LINES.size() + 1;
        assertThat(lines).hasSize(firstDataLine + 2);
        assertThat(lines.get(firstDataLine)).isEqualTo(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute,"
                        + "levenshtein,length,50.0,10.5,NaN,us/op");
        assertThat(lines.get(firstDataLine + 1)).isEqualTo(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute,"
                        + "levenshtein,length,100.0,21.3,0.42,us/op");
    }

    private static List<ClassifiedBenchmarkResult> classifyAll(JmhResultRecord... records) {
        return BenchmarkFamilies.classifyAll(List.of(records));
    }

    private static JmhResultRecord oneRecord() {
        return new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                Map.of("length", "50"), 10.5, Double.NaN, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
    }

    private static JmhResultRecord anotherRecord() {
        return new JmhResultRecord(
                "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                Map.of("length", "100"), 21.3, 0.42, "us/op", "avgt", 1, 3, "1 s", 5, "1 s");
    }
}
