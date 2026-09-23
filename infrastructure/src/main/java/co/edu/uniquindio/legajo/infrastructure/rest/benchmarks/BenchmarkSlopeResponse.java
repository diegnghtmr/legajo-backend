package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkSlope;

import java.util.Objects;

/**
 * Wire shape of one row of {@code GET /api/v1/benchmarks}' {@code slopes[]} (TRD §6.6/TAC-18,
 * fixed by TRD 1.3.10): {@code family, points, empiricalSlope, theoreticalExponent}, exactly
 * the field names and order the TRD row fixes.
 */
public record BenchmarkSlopeResponse(String family, int points, double empiricalSlope, double theoreticalExponent) {

    public BenchmarkSlopeResponse {
        Objects.requireNonNull(family, "family");
    }

    public static BenchmarkSlopeResponse from(BenchmarkSlope slope) {
        Objects.requireNonNull(slope, "slope");
        return new BenchmarkSlopeResponse(
                slope.family(), slope.points(), slope.empiricalSlope(), slope.theoreticalExponent());
    }
}
