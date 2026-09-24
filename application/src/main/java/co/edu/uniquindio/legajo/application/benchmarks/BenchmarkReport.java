package co.edu.uniquindio.legajo.application.benchmarks;

import java.util.List;
import java.util.Objects;

/**
 * The full body of {@code GET /api/v1/benchmarks}: the reference-harness metadata plus every
 * classified JMH result and every curve's log-log slope, exactly as the two versioned CSVs in
 * {@code benchmarks/results/} were exported — this type carries no computation of its own.
 */
public record BenchmarkReport(BenchmarkHarness harness, List<BenchmarkResult> results, List<BenchmarkSlope> slopes) {

    public BenchmarkReport {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(slopes, "slopes");
        results = List.copyOf(results);
        slopes = List.copyOf(slopes);
    }
}
