package co.edu.uniquindio.legajo.application.benchmarks;

import java.util.Objects;

/**
 * One row of {@code GET /api/v1/benchmarks}' {@code slopes[]} (TRD §6.6/TAC-18, fixed by TRD
 * 1.3.10): the least-squares log-log slope of one curve family next to the theoretical
 * complexity exponent TRD §6.3/§6.4/§6.5 document for it, read as-is from the versioned
 * {@code benchmarks/results/slopes.csv} export (same {@code
 * family,points,empiricalSlope,theoreticalExponent} columns, same order and names — see
 * {@code benchmarks.export.SlopesCsvWriter}). A fixed-n SLO family (no theoretical exponent,
 * not a curve) is excluded from that CSV, so it never appears here either.
 */
public record BenchmarkSlope(String family, int points, double empiricalSlope, double theoreticalExponent) {

    public BenchmarkSlope {
        Objects.requireNonNull(family, "family");
    }
}
