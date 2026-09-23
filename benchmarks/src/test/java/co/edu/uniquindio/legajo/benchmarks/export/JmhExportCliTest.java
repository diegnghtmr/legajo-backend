package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the export CLI's argument parsing and end-to-end run (J2). Written before
 * {@link JmhExportCli} exists (odd/tasks/jmh-benchmarks.md, task J2: strict TDD).
 */
class JmhExportCliTest {

    @Test
    void parsesKeyValueArguments() {
        Map<String, String> parsed = JmhExportCli.parseArgs(
                new String[] {"--input=build/results.json", "--resultsCsv=results/jmh-results.csv"});

        assertThat(parsed).containsEntry("input", "build/results.json");
        assertThat(parsed).containsEntry("resultsCsv", "results/jmh-results.csv");
    }

    @Test
    void rejectsAnArgumentWithoutAnEqualsSign() {
        assertThatThrownBy(() -> JmhExportCli.parseArgs(new String[] {"--input"}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runProducesBothCsvFiles(@TempDir Path tempDir) throws IOException {
        Path input = copyFixtureTo(tempDir);
        Path harness = writeHarnessSidecar(tempDir);
        Path resultsCsv = tempDir.resolve("jmh-results.csv");
        Path slopesCsv = tempDir.resolve("slopes.csv");

        JmhExportCli.run(input, harness, resultsCsv, slopesCsv);

        assertThat(Files.isRegularFile(resultsCsv)).isTrue();
        assertThat(Files.isRegularFile(slopesCsv)).isTrue();
        List<String> resultsLines = Files.readAllLines(resultsCsv);
        assertThat(resultsLines).anyMatch(line -> line.equals("benchmark,family,parameter,size,score,error,unit"));
        assertThat(resultsLines).anyMatch(line -> line.contains("levenshtein"));
        List<String> slopesLines = Files.readAllLines(slopesCsv);
        assertThat(slopesLines.get(0)).isEqualTo("family,points,empiricalSlope,theoreticalExponent");
    }

    @Test
    void failsWhenTheHarnessSidecarIsMissing(@TempDir Path tempDir) throws IOException {
        Path input = copyFixtureTo(tempDir);
        Path harness = tempDir.resolve("missing-harness.properties");
        Path resultsCsv = tempDir.resolve("jmh-results.csv");
        Path slopesCsv = tempDir.resolve("slopes.csv");

        assertThatThrownBy(() -> JmhExportCli.run(input, harness, resultsCsv, slopesCsv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(harness.toString());
        assertThat(Files.exists(resultsCsv)).isFalse();
        assertThat(Files.exists(slopesCsv)).isFalse();
    }

    @Test
    void failsWhenTheHarnessSidecarIsOlderThanTheJmhResults(@TempDir Path tempDir) throws IOException {
        Path input = copyFixtureTo(tempDir);
        Path harness = writeHarnessSidecar(tempDir);
        Files.setLastModifiedTime(harness, FileTime.from(Instant.EPOCH));
        Path resultsCsv = tempDir.resolve("jmh-results.csv");
        Path slopesCsv = tempDir.resolve("slopes.csv");

        assertThatThrownBy(() -> JmhExportCli.run(input, harness, resultsCsv, slopesCsv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("older");
        assertThat(Files.exists(resultsCsv)).isFalse();
        assertThat(Files.exists(slopesCsv)).isFalse();
    }

    @Test
    void failsAndWritesNoCsvWhenARecordCannotBeClassified(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("unclassifiable-jmh-results.json");
        Files.writeString(input, """
                [ { "benchmark": "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                    "mode": "avgt", "forks": 1, "warmupIterations": 3, "warmupTime": "1 s",
                    "measurementIterations": 5, "measurementTime": "1 s",
                    "params": { "length": "not-a-number" },
                    "primaryMetric": { "score": 1.0, "scoreError": 0.0, "scoreUnit": "us/op" } } ]
                """);
        Path harness = writeHarnessSidecar(tempDir);
        Path resultsCsv = tempDir.resolve("jmh-results.csv");
        Path slopesCsv = tempDir.resolve("slopes.csv");

        assertThatThrownBy(() -> JmhExportCli.run(input, harness, resultsCsv, slopesCsv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LevenshteinBenchmark.pairwiseCompute")
                .hasMessageContaining("not-a-number");
        assertThat(Files.exists(resultsCsv)).isFalse();
        assertThat(Files.exists(slopesCsv)).isFalse();
    }

    @Test
    void failsWhenTheReportedJdkVersionDoesNotMatchTheHarnessSidecar(@TempDir Path tempDir) throws IOException {
        Path input = tempDir.resolve("jdk-mismatch-jmh-results.json");
        Files.writeString(input, """
                [ { "benchmark": "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                    "mode": "avgt", "forks": 1, "warmupIterations": 3, "warmupTime": "1 s",
                    "measurementIterations": 5, "measurementTime": "1 s",
                    "jdkVersion": "1.8.0_999-definitely-not-the-test-jdk",
                    "params": { "length": "50" },
                    "primaryMetric": { "score": 1.0, "scoreError": 0.0, "scoreUnit": "us/op" } } ]
                """);
        Path harness = writeHarnessSidecar(tempDir);
        Path resultsCsv = tempDir.resolve("jmh-results.csv");
        Path slopesCsv = tempDir.resolve("slopes.csv");

        assertThatThrownBy(() -> JmhExportCli.run(input, harness, resultsCsv, slopesCsv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1.8.0_999-definitely-not-the-test-jdk");
        assertThat(Files.exists(resultsCsv)).isFalse();
        assertThat(Files.exists(slopesCsv)).isFalse();
    }

    private static Path writeHarnessSidecar(Path tempDir) {
        Path harness = tempDir.resolve("harness.properties");
        HarnessInfo.collect().writeSidecar(harness);
        return harness;
    }

    private static Path copyFixtureTo(Path tempDir) throws IOException {
        Path target = tempDir.resolve("sample-jmh-results.json");
        try (InputStream in = JmhJsonResultsReaderTest.class.getResourceAsStream("sample-jmh-results.json")) {
            if (in == null) {
                throw new UncheckedIOException(new IOException("missing test fixture sample-jmh-results.json"));
            }
            Files.copy(in, target);
        }
        return target;
    }
}
