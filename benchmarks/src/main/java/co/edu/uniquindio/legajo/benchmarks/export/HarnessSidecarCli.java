package co.edu.uniquindio.legajo.benchmarks.export;

import java.nio.file.Path;

/**
 * Entry point for the {@code :benchmarks:jmhHarnessSidecar} Gradle task, a finalizer of
 * {@code :benchmarks:jmh}: captures {@link HarnessInfo} right when the JMH run finishes, into
 * a {@code build/results/jmh/harness.properties} sidecar {@link JmhExportCli} reads later.
 * Capturing it here — not when {@code :benchmarks:jmhExport} runs — is what lets the CSV
 * header describe the machine that actually produced the numbers, even when export runs later
 * or on a different machine (R3-harness-captured-at-export-time, odd/tasks/jmh-benchmarks.md).
 * Argument: {@code --output=<path>}.
 */
public final class HarnessSidecarCli {

    private HarnessSidecarCli() {
    }

    public static void main(String[] args) {
        Path output = Path.of(JmhExportCli.require(JmhExportCli.parseArgs(args), "output"));
        run(output);
    }

    /** Collects the current harness and writes it to {@code output}; separated from {@link #main} for testing. */
    static void run(Path output) {
        HarnessInfo.collect().writeSidecar(output);
    }
}
