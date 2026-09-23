package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
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
 * <p>The export is strict: a missing or stale harness sidecar, a JMH-reported JDK version that
 * disagrees with the sidecar, or any benchmark result that cannot be classified all fail the
 * whole export before either CSV is written, rather than producing a silently incomplete file
 * (R4-001/R3-001/R3-002, R3-harness-captured-at-export-time; odd/tasks/jmh-benchmarks.md).
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
        requireFreshHarnessSidecar(harnessSidecar, input);
        HarnessInfo harness = HarnessInfo.readSidecar(harnessSidecar);
        crossCheckReportedJdkVersion(harness, input);

        List<JmhResultRecord> records = JmhJsonResultsReader.read(input);
        List<ClassifiedBenchmarkResult> classified = BenchmarkFamilies.classifyAll(records);
        JmhResultsCsvWriter.write(resultsCsv, harness, classified);
        SlopesCsvWriter.write(slopesCsv, classified);
    }

    /**
     * The harness sidecar must exist and be at least as new as the JMH results it describes:
     * an absent or older sidecar means it was not (re)written for this run, so the CSV header
     * would misreport the machine that actually produced these numbers.
     */
    private static void requireFreshHarnessSidecar(Path harnessSidecar, Path input) {
        if (!Files.isRegularFile(harnessSidecar)) {
            throw new IllegalStateException(
                    "harness sidecar " + harnessSidecar + " is missing; run :benchmarks:jmh before "
                            + ":benchmarks:jmhExport so the reference harness is captured at run time");
        }
        try {
            FileTime harnessTime = Files.getLastModifiedTime(harnessSidecar);
            FileTime inputTime = Files.getLastModifiedTime(input);
            if (harnessTime.compareTo(inputTime) < 0) {
                throw new IllegalStateException(
                        "harness sidecar " + harnessSidecar + " (" + harnessTime + ") is older than the JMH "
                                + "results " + input + " (" + inputTime + "); rerun :benchmarks:jmh so the sidecar "
                                + "matches this run");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "failed to compare timestamps of " + harnessSidecar + " and " + input, e);
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
