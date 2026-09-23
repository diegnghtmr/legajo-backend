package co.edu.uniquindio.legajo.benchmarks.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Wire shape of one JMH result entry's {@code primaryMetric} object. {@code scoreError} is
 * bound as {@code double}, not {@code String}: with a single fork JMH writes it as the
 * non-standard bare JSON token {@code NaN} (not a quoted string), which
 * {@link JmhJsonResultsReader} configures its {@code JsonMapper} to accept.
 *
 * <p>{@code score} is boxed ({@code Double}, not {@code double}) so a JMH JSON entry missing
 * the field binds to {@code null} instead of silently defaulting to {@code 0.0}; {@link
 * JmhRawEntry#toResultRecord()} rejects a {@code null} score with a clear message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record JmhRawMetric(Double score, double scoreError, String scoreUnit) {
}
