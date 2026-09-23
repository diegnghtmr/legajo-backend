package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for the {@code :benchmarks:jmhHarnessSidecar} entry point: it must capture
 * {@link HarnessInfo} right when {@code :benchmarks:jmh} runs, into a sidecar
 * {@link JmhExportCli} later reads, instead of whatever machine happens to run
 * {@code :benchmarks:jmhExport}.
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

    /** The sidecar this CLI writes must be bound to the exact JMH results file it was told to
     * describe, not just carry a fresher timestamp than it: a rerun after a failed
     * {@code :benchmarks:jmh} task would still produce a fresher-looking sidecar next to a
     * stale, unrelated results file. */
    @Test
    void theWrittenSidecarIsBoundToTheGivenJmhResultsFile(@TempDir Path tempDir) throws IOException {
        Path output = tempDir.resolve("harness.properties");
        Path jmhResults = writeJmhResultsFixture(tempDir);

        HarnessSidecarCli.run(output, jmhResults);

        assertThat(HarnessInfo.readRecordedJmhResultsSha256(output))
                .isEqualTo(HarnessInfo.sha256Hex(jmhResults));
    }

    /**
     * {@code main} shares {@link JmhExportCli#require} with {@link JmhExportCli}'s own entry
     * point, so a missing {@code --output} must fail with a message naming that exact argument
     * instead of an unhelpful {@link NullPointerException} once {@code main} tries to use it.
     */
    @Test
    void mainFailsWhenTheOutputArgumentIsMissing() {
        assertThatThrownBy(() -> HarnessSidecarCli.main(new String[] {"--input=jmh-results.json"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--output");
    }

    @Test
    void mainFailsWhenTheInputArgumentIsMissing() {
        assertThatThrownBy(() -> HarnessSidecarCli.main(new String[] {"--output=harness.properties"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--input");
    }

    @Test
    void mainFailsWhenTheOutputArgumentIsBlank() {
        assertThatThrownBy(() -> HarnessSidecarCli.main(
                new String[] {"--output=", "--input=jmh-results.json"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--output");
    }

    @Test
    void mainFailsWhenTheInputArgumentIsBlank() {
        assertThatThrownBy(() -> HarnessSidecarCli.main(
                new String[] {"--output=harness.properties", "--input="}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--input");
    }

    private static Path writeJmhResultsFixture(Path tempDir) throws IOException {
        Path jmhResults = tempDir.resolve("jmh-results.json");
        Files.writeString(jmhResults, "[ { \"benchmark\": \"x\" } ]");
        return jmhResults;
    }
}
