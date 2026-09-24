/**
 * Deterministic input builders for the JMH benchmarks, following the fixed performance-test
 * protocol: synthetic token sequences and unit vectors built from a fixed seed, plus a loader
 * for the real reference corpus (n = 20) used by the classic-pairwise and clustering SLO
 * benchmarks. Kept separate from the {@code @Benchmark} classes themselves (under
 * {@code src/jmh/java}) so this logic can be unit-tested with plain JUnit.
 */
@NullMarked
package co.edu.uniquindio.legajo.benchmarks.input;

import org.jspecify.annotations.NullMarked;
