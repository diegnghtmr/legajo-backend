package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the reference-harness metadata the CSV header reports (TRD NFR-QA-10:
 * "el arnés de referencia definido en la cabecera del CSV de mediciones y en el README").
 * Written before {@link HarnessInfo} exists (odd/tasks/jmh-benchmarks.md, task J2: strict
 * TDD). The exact CPU/RAM/OS values are environment-dependent, so these tests assert shape
 * and plausibility rather than fixed values.
 */
class HarnessInfoTest {

    @Test
    void collectsANonBlankCpuModel() {
        HarnessInfo info = HarnessInfo.collect();

        assertThat(info.cpuModel()).isNotBlank();
    }

    @Test
    void collectsAPositiveLogicalCoreCount() {
        HarnessInfo info = HarnessInfo.collect();

        assertThat(info.logicalCores()).isPositive();
    }

    @Test
    void collectsAPositiveTotalRamInBytes() {
        HarnessInfo info = HarnessInfo.collect();

        assertThat(info.totalRamBytes()).isPositive();
    }

    @Test
    void collectsNonBlankJdkAndOsDescriptions() {
        HarnessInfo info = HarnessInfo.collect();

        assertThat(info.jdkVendorAndVersion()).isNotBlank();
        assertThat(info.operatingSystem()).isNotBlank();
    }

    @Test
    void collectsAUtcTimestamp() {
        HarnessInfo info = HarnessInfo.collect();

        assertThat(info.utcDate()).isNotBlank();
        assertThat(info.utcDate()).contains("T");
    }

    @Test
    void formatsAsHeaderLinesEachPrefixedWithAHash() {
        HarnessInfo info = HarnessInfo.collect();

        for (String line : info.toHeaderLines()) {
            assertThat(line).startsWith("#");
        }
    }

    @Test
    void readsCpuModelNameFromAProcCpuinfoStyleFile(@TempDir Path tempDir) throws IOException {
        Path cpuinfo = tempDir.resolve("cpuinfo");
        Files.writeString(cpuinfo, "processor\t: 0\nmodel name\t: Test CPU Model\ncache size\t: 512 KB\n");

        Optional<String> modelName = HarnessInfo.readCpuModelNameFrom(cpuinfo);

        assertThat(modelName).contains("Test CPU Model");
    }

    @Test
    void returnsEmptyWhenTheCpuinfoFileDoesNotExist(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");

        Optional<String> modelName = HarnessInfo.readCpuModelNameFrom(missing);

        assertThat(modelName).isEmpty();
    }

    @Test
    void returnsEmptyInsteadOfThrowingWhenTheCpuinfoPathIsADirectory(@TempDir Path tempDir) {
        Optional<String> modelName = HarnessInfo.readCpuModelNameFrom(tempDir);

        assertThat(modelName).isEmpty();
    }

    @Test
    void writesAndReadsBackASidecarRoundTrip(@TempDir Path tempDir) {
        Path sidecar = tempDir.resolve("harness.properties");
        HarnessInfo original = HARNESS_SAMPLE;

        original.writeSidecar(sidecar);
        HarnessInfo readBack = HarnessInfo.readSidecar(sidecar);

        assertThat(readBack).isEqualTo(original);
    }

    @Test
    void failsToReadASidecarMissingAField(@TempDir Path tempDir) throws IOException {
        Path sidecar = tempDir.resolve("harness.properties");
        Files.writeString(sidecar, "cpuModel=Test CPU\n");

        assertThatThrownBy(() -> HarnessInfo.readSidecar(sidecar))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logicalCores");
    }

    @Test
    void failsToReadASidecarWithANonNumericLogicalCoresNamingTheSidecarAndKey(@TempDir Path tempDir) throws IOException {
        Path sidecar = tempDir.resolve("harness.properties");
        Files.writeString(sidecar, """
                cpuModel=Test CPU
                logicalCores=not-a-number
                totalRamBytes=8000000000
                jdk=Temurin 25
                os=Linux 6.0 (amd64)
                utcDate=2026-09-22T00:00:00Z
                """);

        assertThatThrownBy(() -> HarnessInfo.readSidecar(sidecar))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(sidecar.toString())
                .hasMessageContaining("logicalCores")
                .hasMessageContaining("not-a-number");
    }

    @Test
    void failsToReadASidecarWithANonNumericTotalRamBytesNamingTheSidecarAndKey(@TempDir Path tempDir) throws IOException {
        Path sidecar = tempDir.resolve("harness.properties");
        Files.writeString(sidecar, """
                cpuModel=Test CPU
                logicalCores=4
                totalRamBytes=not-a-number
                jdk=Temurin 25
                os=Linux 6.0 (amd64)
                utcDate=2026-09-22T00:00:00Z
                """);

        assertThatThrownBy(() -> HarnessInfo.readSidecar(sidecar))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(sidecar.toString())
                .hasMessageContaining("totalRamBytes")
                .hasMessageContaining("not-a-number");
    }

    @Test
    void writesASidecarBoundToTheGivenJmhResultsFileAndReadingItBackAgrees(@TempDir Path tempDir) throws IOException {
        Path sidecar = tempDir.resolve("harness.properties");
        Path jmhResults = tempDir.resolve("jmh-results.json");
        Files.writeString(jmhResults, "[ { \"benchmark\": \"x\" } ]");

        HARNESS_SAMPLE.writeSidecar(sidecar, jmhResults);

        assertThat(HarnessInfo.readSidecar(sidecar)).isEqualTo(HARNESS_SAMPLE);
        assertThat(HarnessInfo.readRecordedJmhResultsSha256(sidecar))
                .isEqualTo(HarnessInfo.sha256Hex(jmhResults));
    }

    @Test
    void sha256HexChangesWhenTheFileContentChanges(@TempDir Path tempDir) throws IOException {
        Path fileA = tempDir.resolve("a.json");
        Path fileB = tempDir.resolve("b.json");
        Files.writeString(fileA, "content-a");
        Files.writeString(fileB, "content-b");

        assertThat(HarnessInfo.sha256Hex(fileA)).isNotEqualTo(HarnessInfo.sha256Hex(fileB));
    }

    @Test
    void readRecordedJmhResultsSha256FailsWhenTheSidecarWasWrittenWithoutABinding(@TempDir Path tempDir) {
        Path sidecar = tempDir.resolve("harness.properties");
        HARNESS_SAMPLE.writeSidecar(sidecar);

        assertThatThrownBy(() -> HarnessInfo.readRecordedJmhResultsSha256(sidecar))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(sidecar.toString())
                .hasMessageContaining("jmhResultsSha256");
    }

    private static final HarnessInfo HARNESS_SAMPLE =
            new HarnessInfo("Test CPU", 4, 8_000_000_000L, "Temurin 25", "Linux 6.0 (amd64)", "2026-09-22T00:00:00Z");
}
