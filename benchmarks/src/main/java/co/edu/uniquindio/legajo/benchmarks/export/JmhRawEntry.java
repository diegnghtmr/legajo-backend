package co.edu.uniquindio.legajo.benchmarks.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * Wire shape of one entry of JMH's own {@code -rf JSON} results array. Only the fields this
 * exporter needs are declared; every other field JMH writes (raw samples, percentiles,
 * confidence intervals, JVM args, ...) is ignored via {@link JsonIgnoreProperties}.
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
        JmhRawMetric primaryMetric) {

    JmhResultRecord toResultRecord() {
        return new JmhResultRecord(
                benchmark, params == null ? Map.of() : params, primaryMetric.score(), primaryMetric.scoreError(),
                primaryMetric.scoreUnit(), mode, forks, warmupIterations, warmupTime, measurementIterations,
                measurementTime);
    }
}
