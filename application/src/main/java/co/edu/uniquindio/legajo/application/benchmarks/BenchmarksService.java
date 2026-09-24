package co.edu.uniquindio.legajo.application.benchmarks;

import java.util.Objects;

/**
 * Use case behind {@code GET /api/v1/benchmarks}. Pure orchestration, like
 * {@code EmbeddingsService}: no Spring, no HTTP, no file I/O of its own —
 * it only forwards to whichever {@link BenchmarkReportRepository} the composition root wires
 * in, so the REST layer never depends on the CSV-reading adapter directly.
 */
public final class BenchmarksService {

    private final BenchmarkReportRepository benchmarkReportRepository;

    public BenchmarksService(BenchmarkReportRepository benchmarkReportRepository) {
        this.benchmarkReportRepository = Objects.requireNonNull(benchmarkReportRepository, "benchmarkReportRepository");
    }

    /** Loads the benchmark report, unchanged from whatever the repository returns. */
    public BenchmarkReport report() {
        return benchmarkReportRepository.load();
    }
}
