package co.edu.uniquindio.legajo;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarkReportRepository;

import java.util.Objects;

/**
 * TRD §6.6, fixed by TRD 1.3.10 ("si los archivos faltan o están mal formados, el servidor
 * falla al arrancar, en lugar de publicar una pantalla vacía"): loads the benchmark report
 * once, at startup, exactly the same way {@code BenchmarksController} does per request, so a
 * missing or malformed {@code benchmarks/results/jmh-results.csv}/{@code slopes.csv} export
 * stops the boot instead of only surfacing as a 500 on the first {@code GET
 * /api/v1/benchmarks} request.
 *
 * <p>Mirrors {@link EmbeddingCacheStartupValidator}'s shape for the same reason: {@code
 * DomainConfiguration} is the one place that wires this to Spring's {@code
 * SmartInitializingSingleton} startup hook, so a plain {@link IllegalStateException} thrown
 * from {@link #validate()} — {@code CsvBenchmarkReportRepository}'s own exception, naming the
 * offending file and the export command — aborts context refresh with no framework-specific
 * wrapping added here. Simpler than {@code EmbeddingCacheStartupValidator}: there is only one
 * {@link BenchmarkReportRepository} bean (no cached/live mode branching, no local/api split).
 */
final class BenchmarksStartupValidator {

    private final BenchmarkReportRepository benchmarkReportRepository;

    BenchmarksStartupValidator(BenchmarkReportRepository benchmarkReportRepository) {
        this.benchmarkReportRepository = Objects.requireNonNull(benchmarkReportRepository, "benchmarkReportRepository");
    }

    void validate() {
        benchmarkReportRepository.load();
    }
}
