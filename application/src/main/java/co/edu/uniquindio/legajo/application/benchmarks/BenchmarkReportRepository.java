package co.edu.uniquindio.legajo.application.benchmarks;

/**
 * Output port for the JMH benchmark report {@code GET /api/v1/benchmarks} serves (TRD §6.6,
 * fixed by TRD 1.3.10): loads the versioned {@code benchmarks/results/jmh-results.csv} and
 * {@code slopes.csv} exports as one {@link BenchmarkReport}. The single infrastructure
 * adapter, {@code CsvBenchmarkReportRepository}, never runs JMH and never recalculates
 * anything — it only parses the two files a prior {@code :benchmarks:jmh :benchmarks:jmhExport}
 * run already produced.
 *
 * <p><b>Placed in {@code application}, not {@code domain} (author decision, flagged).</b>
 * Every other output port in this codebase ({@code CorpusRepository}, {@code
 * EmbeddingRepository}) lives in {@code domain.port} because the data it returns backs a
 * hand-written algorithm ArchUnit's {@code domainHasNoFrameworkDependency} rule protects.
 * This port backs no algorithm at all — {@link BenchmarkReport} is a pure passthrough of
 * externally produced measurement data, with nothing here for that rule, or the fixed
 * {@code domain} package list in {@code ArchitectureTest}, to protect. Keeping it in
 * {@code application} avoids widening that fixed domain package list for a feature with no
 * business rule to enforce, while still keeping the CSV-reading detail (infrastructure)
 * behind an interface the REST layer's use case depends on, not the concrete adapter.
 */
public interface BenchmarkReportRepository {

    /**
     * Loads the current benchmark report. Throws {@link IllegalStateException} naming the
     * offending file and the export command to re-run when a results/slopes file is missing
     * or malformed (missing harness header key, wrong column count, non-numeric value) — the
     * same fail-closed contract {@code EmbeddingCacheStartupValidator} applies to a
     * missing/malformed embedding cache (TRD §9).
     */
    BenchmarkReport load();
}
