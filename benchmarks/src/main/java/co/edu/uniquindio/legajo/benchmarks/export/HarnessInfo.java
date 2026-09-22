package co.edu.uniquindio.legajo.benchmarks.export;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
        return readProcCpuInfoModelName().orElse("unknown (" + System.getProperty("os.arch", "unknown") + ")");
    }

    /** Linux-only: {@code /proc/cpuinfo}'s first {@code model name} line. Empty on any other OS or failure. */
    private static Optional<String> readProcCpuInfoModelName() {
        Path cpuInfo = Path.of("/proc/cpuinfo");
        if (!Files.isReadable(cpuInfo)) {
            return Optional.empty();
        }
        try {
            return Files.lines(cpuInfo)
                    .filter(line -> line.startsWith("model name"))
                    .map(line -> line.substring(line.indexOf(':') + 1).strip())
                    .findFirst();
        } catch (IOException e) {
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
