package co.edu.uniquindio.legajo.benchmarks.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Wire shape of one entry of JMH's own {@code -rf JSON} results array. Only the fields this
 * exporter needs are declared; every other field JMH writes (raw samples, percentiles,
 * confidence intervals, JVM args, ...) is ignored via {@link JsonIgnoreProperties}.
 *
 * <p>{@code jdkVersion} is the JDK version JMH itself recorded while running (may be
 * {@code null} on older or trimmed JSON): {@link JmhJsonResultsReader#readReportedJdkVersion}
 * exposes it so {@link JmhExportCli} can cross-check it against the harness sidecar
 * {@link HarnessInfo} captured at {@code :benchmarks:jmh} run time
 * (R3-harness-captured-at-export-time).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record JmhRawEntry(
        String benchmark,
        String mode,
        int forks,
        int warmupIterations,
        String warmupTime,
        int measurementIterations,
        String measurementTime,
        Map<String, String> params,
        JmhRawMetric primaryMetric,
        String jdkVersion) {

    JmhResultRecord toResultRecord() {
        if (primaryMetric == null) {
            throw new IllegalStateException(
                    "JMH result entry for benchmark '" + benchmark + "' is missing primaryMetric");
        }
        Double score = primaryMetric.score();
        if (score == null) {
            throw new IllegalStateException(
                    "JMH result entry for benchmark '" + benchmark + "' is missing primaryMetric.score");
        }
        return new JmhResultRecord(
                benchmark, params == null ? Map.of() : params, score, primaryMetric.scoreError(),
                primaryMetric.scoreUnit(), mode, forks, warmupIterations, warmupTime, measurementIterations,
                measurementTime);
    }
}
