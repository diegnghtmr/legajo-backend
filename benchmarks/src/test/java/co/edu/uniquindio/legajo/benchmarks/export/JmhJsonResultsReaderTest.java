package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the JMH JSON results reader (J2). Written before
 * {@link JmhJsonResultsReader} exists (odd/tasks/jmh-benchmarks.md, task J2: strict TDD).
 * The fixture is a trimmed-down, but schema-faithful, JMH {@code -rf JSON} output, including
 * JMH's own non-standard bare {@code NaN} token for a single-fork {@code scoreError}.
 */
class JmhJsonResultsReaderTest {

    @Test
    void readsEveryEntryOfTheResultsFile(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        List<JmhResultRecord> records = JmhJsonResultsReader.read(resultsFile);

        assertThat(records).hasSize(3);
    }

    @Test
    void parsesThePairwiseEntryFields(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        JmhResultRecord first = JmhJsonResultsReader.read(resultsFile).get(0);

        assertThat(first.benchmark())
                .isEqualTo("co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute");
        assertThat(first.params()).containsExactly(java.util.Map.entry("length", "50"));
        assertThat(first.score()).isEqualTo(10.5);
        assertThat(first.unit()).isEqualTo("us/op");
        assertThat(first.mode()).isEqualTo("avgt");
        assertThat(first.forks()).isEqualTo(1);
        assertThat(first.warmupIterations()).isEqualTo(3);
        assertThat(first.measurementIterations()).isEqualTo(5);
    }

    @Test
    void parsesABareNanScoreErrorAsDoubleNan(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        JmhResultRecord first = JmhJsonResultsReader.read(resultsFile).get(0);

        assertThat(first.error()).isNaN();
    }

    @Test
    void parsesANumericScoreError(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        JmhResultRecord second = JmhJsonResultsReader.read(resultsFile).get(1);

        assertThat(second.error()).isEqualTo(0.42);
    }

    @Test
    void parsesMultiValuedParams(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        JmhResultRecord third = JmhJsonResultsReader.read(resultsFile).get(2);

        assertThat(third.benchmark())
                .isEqualTo("co.edu.uniquindio.legajo.benchmarks.hac.LanceWilliamsBenchmark.agglomerate");
        assertThat(third.params()).containsEntry("n", "20").containsEntry("criterion", "ward");
    }

    @Test
    void wrapsMalformedJsonWithTheResultsFilePath(@TempDir Path tempDir) throws IOException {
        Path resultsFile = tempDir.resolve("broken-jmh-results.json");
        Files.writeString(resultsFile, "{ this is not valid JMH JSON ");

        // R3-005: assert the exception type the reader's catch produces, not just the message.
        assertThatThrownBy(() -> JmhJsonResultsReader.read(resultsFile))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(resultsFile.toString());
    }

    @Test
    void readsNoReportedJdkVersionWhenNoEntryDeclaresOne(@TempDir Path tempDir) throws IOException {
        Path resultsFile = copyFixtureTo(tempDir);

        Optional<String> jdkVersion = JmhJsonResultsReader.readReportedJdkVersion(resultsFile);

        assertThat(jdkVersion).isEmpty();
    }

    @Test
    void readsTheReportedJdkVersionWhenAnEntryDeclaresOne(@TempDir Path tempDir) throws IOException {
        Path resultsFile = tempDir.resolve("jdk-version-jmh-results.json");
        Files.writeString(resultsFile, """
                [ { "benchmark": "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute",
                    "mode": "avgt", "forks": 1, "warmupIterations": 3, "warmupTime": "1 s",
                    "measurementIterations": 5, "measurementTime": "1 s", "jdkVersion": "25.0.4",
                    "params": { "length": "50" },
                    "primaryMetric": { "score": 1.0, "scoreError": 0.0, "scoreUnit": "us/op" } } ]
                """);

        Optional<String> jdkVersion = JmhJsonResultsReader.readReportedJdkVersion(resultsFile);

        assertThat(jdkVersion).contains("25.0.4");
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
