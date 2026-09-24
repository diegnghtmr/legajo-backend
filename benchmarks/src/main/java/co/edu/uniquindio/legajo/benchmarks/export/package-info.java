/**
 * Exports one JMH run's JSON results into the two versioned CSVs the technical
 * documentation reads: {@code benchmarks/results/jmh-results.csv} (harness header +
 * one row per benchmark data point) and {@code benchmarks/results/slopes.csv} (the log-log
 * slope of each empirical curve against its theoretical exponent).
 */
@NullMarked
package co.edu.uniquindio.legajo.benchmarks.export;

import org.jspecify.annotations.NullMarked;
