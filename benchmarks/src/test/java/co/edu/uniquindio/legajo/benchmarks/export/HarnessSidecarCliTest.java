package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the {@code :benchmarks:jmhHarnessSidecar} entry point (R3-harness-captured-at-
 * export-time, odd/tasks/jmh-benchmarks.md): it must capture {@link HarnessInfo} right when
 * {@code :benchmarks:jmh} runs, into a sidecar {@link JmhExportCli} later reads, instead of
 * whatever machine happens to run {@code :benchmarks:jmhExport}.
 */
class HarnessSidecarCliTest {

    @Test
    void writesAReadableHarnessSidecarFile(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("harness.properties");
        Path jmhResults = writeJmhResultsFixture(tempDir);

        HarnessSidecarCli.run(output, jmhResults);

        assertThat(Files.isRegularFile(output)).isTrue();
        HarnessInfo readBack = HarnessInfo.readSidecar(output);
        assertThat(readBack.cpuModel()).isNotBlank();
        assertThat(readBack.logicalCores()).isPositive();
    }

    /** R4-sidecar-finalizer-refreshes-on-failed-jmh / R2-001 / R3-003: the sidecar this CLI
     * writes must be bound to the exact JMH results file it was told to describe, not just
     * carry a fresher timestamp than it. */
    @Test
    void theWrittenSidecarIsBoundToTheGivenJmhResultsFile(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("harness.properties");
        Path jmhResults = writeJmhResultsFixture(tempDir);

        HarnessSidecarCli.run(output, jmhResults);

        assertThat(HarnessInfo.readRecordedJmhResultsSha256(output))
                .isEqualTo(HarnessInfo.sha256Hex(jmhResults));
    }

    private static Path writeJmhResultsFixture(Path tempDir) throws IOException {
        Path jmhResults = tempDir.resolve("jmh-results.json");
        Files.writeString(jmhResults, "[ { \"benchmark\": \"x\" } ]");
        return jmhResults;
    }
}
