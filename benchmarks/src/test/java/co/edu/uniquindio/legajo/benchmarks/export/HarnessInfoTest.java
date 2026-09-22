package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
