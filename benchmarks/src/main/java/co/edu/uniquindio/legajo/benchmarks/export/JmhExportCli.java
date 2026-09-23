package co.edu.uniquindio.legajo.benchmarks.export;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Entry point for the {@code :benchmarks:jmhExport} Gradle task: reads one JMH JSON results
 * file and writes {@code benchmarks/results/jmh-results.csv} and
 * {@code benchmarks/results/slopes.csv} (TRD NFR-QA-10, TAC-18; odd/tasks/jmh-benchmarks.md,
 * task J2). Arguments: {@code --input=<path>}, {@code --harness=<path>} (the sidecar
 * {@code :benchmarks:jmhHarnessSidecar} wrote at {@code :benchmarks:jmh} run time),
 * {@code --resultsCsv=<path>}, {@code --slopesCsv=<path>}.
 *
 * <p>The export is strict: a missing harness sidecar, a sidecar bound to a different JMH
 * results file, a JMH-reported JDK version that disagrees with the sidecar, or any benchmark
 * result that cannot be classified all fail the whole export before either CSV is written,
 * rather than producing a silently incomplete file (R4-001/R3-001/R3-002,
 * R3-harness-captured-at-export-time, odd/tasks/jmh-benchmarks.md).
 */
public final class JmhExportCli {

    private JmhExportCli() {
    }

    public static void main(String[] args) {
        Map<String, String> parsed = parseArgs(args);
        Path input = Path.of(require(parsed, "input"));
        Path harness = Path.of(require(parsed, "harness"));
        Path resultsCsv = Path.of(require(parsed, "resultsCsv"));
        Path slopesCsv = Path.of(require(parsed, "slopesCsv"));
        run(input, harness, resultsCsv, slopesCsv);
    }

    /** Reads {@code input} and writes both CSVs; separated from {@link #main} so it is directly testable. */
    static void run(Path input, Path harnessSidecar, Path resultsCsv, Path slopesCsv) {
        requireHarnessSidecarBoundToJmhResults(harnessSidecar, input);
        HarnessInfo harness = HarnessInfo.readSidecar(harnessSidecar);
        crossCheckReportedJdkVersion(harness, input);

        List<JmhResultRecord> records = JmhJsonResultsReader.read(input);
        List<ClassifiedBenchmarkResult> classified = BenchmarkFamilies.classifyAll(records);
        JmhResultsCsvWriter.write(resultsCsv, harness, classified);
        SlopesCsvWriter.write(slopesCsv, classified);
    }

    /**
     * The harness sidecar must exist and its recorded {@code jmhResultsSha256} must match
     * {@code input}'s actual content (R4-sidecar-finalizer-refreshes-on-failed-jmh / R2-001 /
     * R3-003, odd/tasks/jmh-benchmarks.md): a plain timestamp comparison cannot tell a sidecar
     * that legitimately describes {@code input} apart from one refreshed by a finalizer that
     * ran after a failed {@code :benchmarks:jmh} task against a stale, unrelated results file —
     * that finalizer run still leaves the sidecar with a newer mtime than the stale JSON, even
     * though the two no longer describe the same run. Content, not mtime, is the only thing
     * that can prove the pairing.
     */
    private static void requireHarnessSidecarBoundToJmhResults(Path harnessSidecar, Path input) {
        if (!Files.isRegularFile(harnessSidecar)) {
            throw new IllegalStateException(
                    "harness sidecar " + harnessSidecar + " is missing; run :benchmarks:jmh before "
                            + ":benchmarks:jmhExport so the reference harness is captured at run time");
        }
        String recordedSha256 = HarnessInfo.readRecordedJmhResultsSha256(harnessSidecar);
        String actualSha256 = HarnessInfo.sha256Hex(input);
        if (!recordedSha256.equals(actualSha256)) {
            throw new IllegalStateException(
                    "harness sidecar " + harnessSidecar + " was captured for a different JMH results file than "
                            + input + " (recorded SHA-256 '" + recordedSha256 + "' does not match this file's '"
                            + actualSha256 + "'); rerun :benchmarks:jmh so the sidecar matches this run");
        }
    }

    /**
     * A cheap sanity check: when the JMH JSON itself reports a {@code jdkVersion}, it must
     * agree with the harness sidecar's recorded JDK, since both are supposed to describe the
     * same run.
     */
    private static void crossCheckReportedJdkVersion(HarnessInfo harness, Path input) {
        Optional<String> reportedJdkVersion = JmhJsonResultsReader.readReportedJdkVersion(input);
        reportedJdkVersion.ifPresent(reported -> {
            if (!harness.jdkVendorAndVersion().contains(reported)) {
                throw new IllegalStateException(
                        "JMH results " + input + " report jdkVersion '" + reported + "', which does not match "
                                + "the harness sidecar's JDK '" + harness.jdkVendorAndVersion() + "'");
            }
        });
    }

    /** Parses {@code --key=value} arguments into a map, in encounter order. */
    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> parsed = new LinkedHashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("expected --key=value, got: " + arg);
            }
            String[] parts = arg.substring(2).split("=", 2);
            parsed.put(parts[0], parts[1]);
        }
        return parsed;
    }

    /** Package-visible so {@link HarnessSidecarCli} reuses the same argument parsing. */
    static String require(Map<String, String> parsed, String key) {
        String value = parsed.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing required --" + key + "=<value>");
        }
        return value;
    }
}
