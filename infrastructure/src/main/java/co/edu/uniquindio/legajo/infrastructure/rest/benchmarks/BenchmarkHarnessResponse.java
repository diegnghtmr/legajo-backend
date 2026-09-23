package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkHarness;

import java.util.Objects;

/**
 * Wire shape of {@code GET /api/v1/benchmarks}' {@code harness} object (TRD §6.6, fixed by
 * TRD 1.3.10): {@code cpuModel, logicalCores, totalRamBytes, jdk, os, measuredAt}, exactly
 * the field names and order the TRD row fixes.
 */
public record BenchmarkHarnessResponse(
        String cpuModel,
        int logicalCores,
        long totalRamBytes,
        String jdk,
        String os,
        String measuredAt) {

    public BenchmarkHarnessResponse {
        Objects.requireNonNull(cpuModel, "cpuModel");
        Objects.requireNonNull(jdk, "jdk");
        Objects.requireNonNull(os, "os");
        Objects.requireNonNull(measuredAt, "measuredAt");
    }

    public static BenchmarkHarnessResponse from(BenchmarkHarness harness) {
        Objects.requireNonNull(harness, "harness");
        return new BenchmarkHarnessResponse(
                harness.cpuModel(), harness.logicalCores(), harness.totalRamBytes(), harness.jdk(), harness.os(),
                harness.measuredAt());
    }
}
