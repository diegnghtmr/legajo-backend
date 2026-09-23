package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void writesAReadableHarnessSidecarFile(@TempDir Path tempDir) {
        Path output = tempDir.resolve("harness.properties");

        HarnessSidecarCli.run(output);

        assertThat(Files.isRegularFile(output)).isTrue();
        HarnessInfo readBack = HarnessInfo.readSidecar(output);
        assertThat(readBack.cpuModel()).isNotBlank();
        assertThat(readBack.logicalCores()).isPositive();
    }
}
