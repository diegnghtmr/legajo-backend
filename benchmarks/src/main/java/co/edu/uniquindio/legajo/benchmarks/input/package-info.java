/**
 * Deterministic input builders for the JMH benchmarks (TRD §6.3/§6.4's "Protocolo de
 * pruebas de rendimiento (fijado)"): synthetic token sequences and unit vectors built from a
 * fixed seed, plus a loader for the real reference corpus (n = 20) used by the NFR-QA-01 and
 * NFR-QA-02 SLO benchmarks. Kept separate from the {@code @Benchmark} classes themselves
 * (under {@code src/jmh/java}) so this logic can be unit-tested with plain JUnit, as the
 * feature document (odd/tasks/jmh-benchmarks.md, task J1) requires.
 */
@NullMarked
package co.edu.uniquindio.legajo.benchmarks.input;

import org.jspecify.annotations.NullMarked;
