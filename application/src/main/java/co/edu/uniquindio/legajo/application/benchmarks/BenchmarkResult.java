package co.edu.uniquindio.legajo.application.benchmarks;

import java.util.Objects;

/**
 * One row of {@code GET /api/v1/benchmarks}' {@code results[]} (TRD §6.6, fixed by TRD
 * 1.3.10): one benchmark method at one parameter value, read as-is from the versioned
 * {@code benchmarks/results/jmh-results.csv} export (its {@code
 * benchmark,family,parameter,size,score,error,unit} columns, same order, same names — see
 * {@code benchmarks.export.JmhResultsCsvWriter}). Includes the {@code slo-*} families
 * (NFR-QA-01/NFR-QA-02) and both embedding-dimension measurements; nothing is recomputed or
 * filtered here.
 */
public record BenchmarkResult(
        String benchmark,
        String family,
        String parameter,
        double size,
        double score,
        double error,
        String unit) {

    public BenchmarkResult {
        Objects.requireNonNull(benchmark, "benchmark");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(unit, "unit");
    }
}
