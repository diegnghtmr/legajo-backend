package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkResult;

import java.util.Objects;

/**
 * Wire shape of one row of {@code GET /api/v1/benchmarks}' {@code results[]}, matching the
 * fixed REST contract: {@code benchmark, family, parameter, size, score, error, unit}, exactly the
 * field names and order that contract fixes.
 */
public record BenchmarkResultResponse(
        String benchmark,
        String family,
        String parameter,
        double size,
        double score,
        double error,
        String unit) {

    public BenchmarkResultResponse {
        Objects.requireNonNull(benchmark, "benchmark");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(unit, "unit");
    }

    public static BenchmarkResultResponse from(BenchmarkResult result) {
        Objects.requireNonNull(result, "result");
        return new BenchmarkResultResponse(
                result.benchmark(), result.family(), result.parameter(), result.size(), result.score(),
                result.error(), result.unit());
    }
}
