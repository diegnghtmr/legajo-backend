package co.edu.uniquindio.legajo.benchmarks.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wire shape of one JMH result entry's {@code primaryMetric} object. {@code scoreError} is
 * bound as {@code double}, not {@code String}: with a single fork JMH writes it as the
 * non-standard bare JSON token {@code NaN} (not a quoted string), which
 * {@link JmhJsonResultsReader} configures its {@code JsonMapper} to accept.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record JmhRawMetric(double score, double scoreError, String scoreUnit) {
}
