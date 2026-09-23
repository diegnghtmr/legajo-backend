package co.edu.uniquindio.legajo.benchmarks.export;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the JMH raw-entry-to-result-record conversion, hardening it against a
 * malformed JMH JSON entry (odd/tasks/jmh-benchmarks.md, task J2h: {@code
 * R3-raw-entry-missing-fields}): a missing {@code primaryMetric} or a missing numeric
 * {@code score} must fail with a clear message naming the benchmark entry, never silently
 * become {@code 0.0}.
 */
class JmhRawEntryTest {

    private static final String BENCHMARK =
            "co.edu.uniquindio.legajo.benchmarks.pairwise.LevenshteinBenchmark.pairwiseCompute";

    @Test
    void rejectsAMissingPrimaryMetric() {
        JmhRawEntry entry = new JmhRawEntry(
                BENCHMARK, "avgt", 1, 3, "1 s", 5, "1 s", Map.of("length", "50"), null, null);

        assertThatThrownBy(entry::toResultRecord)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BENCHMARK)
                .hasMessageContaining("primaryMetric");
    }

    @Test
    void rejectsAMissingScoreInsteadOfDefaultingToZero() {
        JmhRawMetric metricWithoutScore = new JmhRawMetric(null, 0.1, "us/op");
        JmhRawEntry entry = new JmhRawEntry(
                BENCHMARK, "avgt", 1, 3, "1 s", 5, "1 s", Map.of("length", "50"), metricWithoutScore, null);

        assertThatThrownBy(entry::toResultRecord)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(BENCHMARK)
                .hasMessageContaining("score");
    }
}
