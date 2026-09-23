package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The reference-harness metadata TRD NFR-QA-10 requires in the CSV header and the README
 * (CPU model, logical cores, total RAM, JDK, OS, UTC date): "el arnés de referencia es la
 * máquina registrada en la cabecera del CSV de mediciones y en el README (modelo de CPU,
 * núcleos, RAM, JDK)".
 */
public record HarnessInfo(
        String cpuModel, int logicalCores, long totalRamBytes, String jdkVendorAndVersion, String operatingSystem,
        String utcDate) {

    /** Collects every field from the JVM/OS the current process is running on. */
    public static HarnessInfo collect() {
        return new HarnessInfo(
                detectCpuModel(), Runtime.getRuntime().availableProcessors(), detectTotalRamBytes(), detectJdkVendorAndVersion(),
                detectOperatingSystem(), Instant.now().toString());
    }

    /** This information rendered as {@code #}-prefixed CSV header lines, one field per line. */
    public List<String> toHeaderLines() {
        return List.of(
                "# harness.cpuModel = " + cpuModel,
                "# harness.logicalCores = " + logicalCores,
                "# harness.totalRamBytes = " + totalRamBytes,
                "# harness.jdk = " + jdkVendorAndVersion,
                "# harness.os = " + operatingSystem,
                "# harness.utcDate = " + utcDate);
    }

    /**
     * Writes this harness info as a small {@code key=value} sidecar file, overwriting any
     * existing file at {@code output}. Read back by {@link #readSidecar}. Used by the
     * {@code :benchmarks:jmhHarnessSidecar} Gradle task (a finalizer of {@code :benchmarks:jmh})
     * to record the reference harness at JMH run time rather than later when {@code jmhExport}
     * runs, since {@code jmhExport} can run on a different machine, or hours or days after the
     * benchmark itself, and by then the CPU/RAM/JDK it would detect would no longer describe
     * the machine that actually produced the measurements.
     */
    public void writeSidecar(Path output) {
        writeSidecarLines(output, baseSidecarLines());
    }

    /**
     * Writes this harness info as a sidecar bound to {@code jmhResultsJson}: the same fields
     * {@link #writeSidecar(Path)} writes, plus a {@code jmhResultsSha256} line recording that
     * exact file's SHA-256 ({@link #sha256Hex}). {@code :benchmarks:jmhExport} reads this back
     * with {@link #readRecordedJmhResultsSha256} to refuse a sidecar captured for a different
     * (e.g. stale, or from an unrelated failed run) JMH results file, instead of only comparing
     * file timestamps: a finalizer that reruns after a failed {@code jmh} task would still
     * produce a fresher-looking sidecar file even though it describes a run that never produced
     * new results, so only content, not mtime, can prove the two files match.
     */
    public void writeSidecar(Path output, Path jmhResultsJson) {
        List<String> lines = new ArrayList<>(baseSidecarLines());
        lines.add("jmhResultsSha256=" + sha256Hex(jmhResultsJson));
        writeSidecarLines(output, lines);
    }

    private List<String> baseSidecarLines() {
        return List.of(
                "cpuModel=" + cpuModel,
                "logicalCores=" + logicalCores,
                "totalRamBytes=" + totalRamBytes,
                "jdk=" + jdkVendorAndVersion,
                "os=" + operatingSystem,
                "utcDate=" + utcDate);
    }

    private static void writeSidecarLines(Path output, List<String> lines) {
        try {
            Path parent = output.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(output, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write harness sidecar " + output, e);
        }
    }

    /** The lowercase hex SHA-256 digest of {@code file}'s bytes. */
    public static String sha256Hex(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available on every JDK", e);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to hash " + file, e);
        }
    }

    /**
     * Reads a sidecar file previously written by {@link #writeSidecar(Path)}. Fails with a
     * message naming the sidecar and the missing key when any field is absent, rather than
     * silently defaulting it.
     */
    public static HarnessInfo readSidecar(Path sidecar) {
        Map<String, String> values = readKeyValueLines(sidecar);
        return new HarnessInfo(
                requireField(values, sidecar, "cpuModel"),
                parseIntField(values, sidecar, "logicalCores"),
                parseLongField(values, sidecar, "totalRamBytes"),
                requireField(values, sidecar, "jdk"),
                requireField(values, sidecar, "os"),
                requireField(values, sidecar, "utcDate"));
    }

    /**
     * Reads back the {@code jmhResultsSha256} a sidecar was written with by
     * {@link #writeSidecar(Path, Path)}. Fails naming the sidecar when it was written by the
     * plain {@link #writeSidecar(Path)} (no binding recorded at all).
     */
    public static String readRecordedJmhResultsSha256(Path sidecar) {
        return requireField(readKeyValueLines(sidecar), sidecar, "jmhResultsSha256");
    }

    private static Map<String, String> readKeyValueLines(Path sidecar) {
        Map<String, String> values = new LinkedHashMap<>();
        try {
            for (String line : Files.readAllLines(sidecar, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('=');
                if (separator >= 0) {
                    values.put(line.substring(0, separator), line.substring(separator + 1));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read harness sidecar " + sidecar, e);
        }
        return values;
    }

    private static String requireField(Map<String, String> values, Path sidecar, String key) {
        String value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("harness sidecar " + sidecar + " is missing '" + key + "'");
        }
        return value;
    }

    private static int parseIntField(Map<String, String> values, Path sidecar, String key) {
        String raw = requireField(values, sidecar, key);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "harness sidecar " + sidecar + " has a non-numeric '" + key + "': '" + raw + "'", e);
        }
    }

    private static long parseLongField(Map<String, String> values, Path sidecar, String key) {
        String raw = requireField(values, sidecar, key);
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "harness sidecar " + sidecar + " has a non-numeric '" + key + "': '" + raw + "'", e);
        }
    }

    private static String detectCpuModel() {
        return readCpuModelNameFrom(Path.of("/proc/cpuinfo"))
                .orElse("unknown (" + System.getProperty("os.arch", "unknown") + ")");
    }

    /**
     * Linux-only: {@code cpuInfo}'s (typically {@code /proc/cpuinfo}) first {@code model name}
     * line. Empty when the path is missing, unreadable, not a plain file, or fails while being
     * read (either {@link IOException} at open time or {@link UncheckedIOException} raised by
     * {@link Files#lines} while the stream is consumed). Package-private for direct testing
     * against a fixture file instead of the real {@code /proc/cpuinfo}. The stream is closed
     * via try-with-resources: {@link Files#lines} holds an open file handle until closed.
     */
    static Optional<String> readCpuModelNameFrom(Path cpuInfo) {
        if (!Files.isReadable(cpuInfo)) {
            return Optional.empty();
        }
        try (Stream<String> lines = Files.lines(cpuInfo)) {
            return lines
                    .filter(line -> line.startsWith("model name"))
                    .map(line -> line.substring(line.indexOf(':') + 1).strip())
                    .findFirst();
        } catch (IOException | UncheckedIOException e) {
            return Optional.empty();
        }
    }

    private static long detectTotalRamBytes() {
        try {
            Object osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
                return sunOsBean.getTotalMemorySize();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Non-HotSpot JVM, or the jdk.management module is unavailable: fall through.
        }
        return Runtime.getRuntime().maxMemory();
    }

    private static String detectJdkVendorAndVersion() {
        return System.getProperty("java.vendor", "unknown") + " " + System.getProperty("java.version", "unknown");
    }

    private static String detectOperatingSystem() {
        return System.getProperty("os.name", "unknown") + " " + System.getProperty("os.version", "unknown") + " ("
                + System.getProperty("os.arch", "unknown") + ")";
    }
}
