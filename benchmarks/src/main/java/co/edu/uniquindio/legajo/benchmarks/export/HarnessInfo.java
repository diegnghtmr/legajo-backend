package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
