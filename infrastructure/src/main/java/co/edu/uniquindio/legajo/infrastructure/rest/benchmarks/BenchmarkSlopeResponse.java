package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkSlope;

import java.util.Objects;

/**
 * Wire shape of one row of {@code GET /api/v1/benchmarks}' {@code slopes[]}, matching the
 * fixed REST contract: {@code family, points, empiricalSlope, theoreticalExponent}, exactly
 * the field names and order that contract fixes.
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
