package co.edu.uniquindio.legajo.benchmarks.export;

import java.util.Objects;

/**
 * One {@link JmhResultRecord} paired with the {@link BenchmarkFamily} it was classified into
 * by {@link BenchmarkFamilies#classifyAll}. Both CSV writers ({@link JmhResultsCsvWriter},
 * {@link SlopesCsvWriter}) only ever receive already-classified results: classification
 * failures are resolved once, atomically, before either writer runs (TAC-18; see
 * {@link BenchmarkFamilies#classifyAll} for the strict, fail-the-whole-export contract).
 */
public record ClassifiedBenchmarkResult(JmhResultRecord record, BenchmarkFamily family) {

    public ClassifiedBenchmarkResult {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(family, "family");
    }
}
