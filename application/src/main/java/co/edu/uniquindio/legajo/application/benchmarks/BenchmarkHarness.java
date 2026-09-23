package co.edu.uniquindio.legajo.application.benchmarks;

import java.util.Objects;

/**
 * The reference-harness metadata carried by {@code GET /api/v1/benchmarks} (TRD §6.6, fixed
 * by TRD 1.3.10): the machine the versioned {@code benchmarks/results/jmh-results.csv} export
 * was measured on (NFR-QA-10). Mirrors {@code benchmarks.export.HarnessInfo}'s six fields
 * exactly, field for field, except {@link #measuredAt()} — the CSV header key is
 * {@code utcDate} (that module's own name for "when this run happened"), renamed here to the
 * TRD's fixed wire field name.
 */
public record BenchmarkHarness(
        String cpuModel,
        int logicalCores,
        long totalRamBytes,
        String jdk,
        String os,
        String measuredAt) {

    public BenchmarkHarness {
        Objects.requireNonNull(cpuModel, "cpuModel");
        Objects.requireNonNull(jdk, "jdk");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(measuredAt, "measuredAt");
    }
}
