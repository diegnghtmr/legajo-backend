package co.edu.uniquindio.legajo.infrastructure.rest.benchmarks;

import co.edu.uniquindio.legajo.application.benchmarks.BenchmarksService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * {@code GET /api/v1/benchmarks} (TRD §6.6, fixed by TRD 1.3.10, feature doc task J5): serves
 * the JMH reference-run measurements read from the versioned {@code benchmarks/results/}
 * CSVs. Pure adapter: delegates to {@link BenchmarksService} (application) and only shapes
 * the response as {@link BenchmarkReportResponse}; never runs JMH itself.
 */
@RestController
@RequestMapping("/api/v1/benchmarks")
public class BenchmarksController {

    private final BenchmarksService benchmarksService;

    public BenchmarksController(BenchmarksService benchmarksService) {
        this.benchmarksService = Objects.requireNonNull(benchmarksService, "benchmarksService");
    }

    @GetMapping
    public BenchmarkReportResponse get() {
        return BenchmarkReportResponse.from(benchmarksService.report());
    }
}
