package co.edu.uniquindio.legajo.benchmarks.export;

import java.nio.file.Path;
import java.util.Map;

/**
 * Entry point for the {@code :benchmarks:jmhHarnessSidecar} Gradle task, a finalizer of
 * {@code :benchmarks:jmh}: captures {@link HarnessInfo} right when the JMH run finishes, into
 * a {@code build/results/jmh/harness.properties} sidecar {@link JmhExportCli} reads later.
 * Capturing it here — not when {@code :benchmarks:jmhExport} runs — is what lets the CSV
 * header describe the machine that actually produced the numbers, even when export runs later
 * or on a different machine.
 *
 * <p>The Gradle task only invokes this when {@code :benchmarks:jmh} itself succeeded (see
 * {@code benchmarks/build.gradle.kts}'s {@code onlyIf} on {@code jmhHarnessSidecar}), and the
 * sidecar this CLI writes is bound to the exact JMH results file it is told to describe (its
 * SHA-256, via {@link HarnessInfo#writeSidecar(Path, Path)}), not just given a fresh timestamp
 * — a rerun after a failed {@code jmh} task must never let a stale JSON pass {@link
 * JmhExportCli}'s check just because the sidecar file happens to be newer than it.
 * Arguments: {@code --output=<path>}, {@code --input=<path>} (the JMH results JSON this sidecar
 * describes).
 */
public final class HarnessSidecarCli {

    private HarnessSidecarCli() {
    }

    public static void main(String[] args) {
        Map<String, String> parsed = JmhExportCli.parseArgs(args);
        Path output = Path.of(JmhExportCli.require(parsed, "output"));
        Path input = Path.of(JmhExportCli.require(parsed, "input"));
        run(output, input);
    }

    /**
     * Collects the current harness and writes it as a sidecar bound to {@code jmhResultsJson};
     * separated from {@link #main} for testing.
     */
    static void run(Path output, Path jmhResultsJson) {
        HarnessInfo.collect().writeSidecar(output, jmhResultsJson);
    }
}
