package co.edu.uniquindio.legajo.benchmarks.export;

import java.util.Map;
import java.util.Objects;

/**
 * One parsed JMH result entry (one {@code @Benchmark} method at one {@code @Param}
 * combination), the shape {@link JmhJsonResultsReader} produces from JMH's own JSON output
 * and {@link BenchmarkFamilies}/{@link JmhResultsCsvWriter}/{@link SlopesCsvWriter} consume.
 */
public record JmhResultRecord(
        String benchmark,
        Map<String, String> params,
        double score,
        double error,
        String unit,
        String mode,
        int forks,
        int warmupIterations,
        String warmupTime,
        int measurementIterations,
        String measurementTime) {

    public JmhResultRecord {
        Objects.requireNonNull(benchmark, "benchmark");
        Objects.requireNonNull(params, "params");
        params = Map.copyOf(params);
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(warmupTime, "warmupTime");
        Objects.requireNonNull(measurementTime, "measurementTime");
    }
}
